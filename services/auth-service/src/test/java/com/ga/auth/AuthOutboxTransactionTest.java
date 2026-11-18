package com.ga.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ga.HomeHub.auth.messaging.AuthOutboxEvent;
import com.ga.HomeHub.auth.messaging.AuthOutboxRepository;
import com.ga.HomeHub.auth.messaging.AuthOutboxService;
import com.ga.HomeHub.auth.service.EmailService;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(AuthOutboxTransactionTest.Config.class)
class AuthOutboxTransactionTest{
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=AuthOutboxRepository.class)
    static class Config{
        @Bean
        DataSource dataSource(){
            return new DriverManagerDataSource("jdbc:h2:mem:junit-outbox-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        }
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source){
            LocalContainerEntityManagerFactoryBean factory=new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan(AuthOutboxEvent.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
        @Bean
        KafkaTemplate<String,String> kafka(){return mock(KafkaTemplate.class);}
        @Bean
        AuthOutboxService outbox(AuthOutboxRepository repository,KafkaTemplate<String,String> kafka){
            AuthOutboxService service=new AuthOutboxService(repository,kafka);
            ReflectionTestUtils.setField(service,"topic","junit-only-notifications");
            return service;
        }
        @Bean
        EmailService email(AuthOutboxService outbox){return new EmailService(outbox);}
    }

    @Autowired private EmailService email;
    @Autowired private AuthOutboxService outbox;
    @Autowired private AuthOutboxRepository repository;
    @Autowired private KafkaTemplate<String,String> kafka;
    @Autowired private PlatformTransactionManager manager;

    @BeforeEach
    void clean(){
        repository.deleteAll();
        reset(kafka);
    }

    @Test
    void emailIsNotSentAfterRollback(){
        new TransactionTemplate(manager).executeWithoutResult(status->{
            email.sendEmail("recipient@homehub.example","Test","Rollback body");
            assertEquals(1,repository.count());
            verifyNoInteractions(kafka);
            status.setRollbackOnly();
        });
        assertEquals(0,repository.count());
        verifyNoInteractions(kafka);
    }

    @Test
    void emailIsSentAfterCommit()throws Exception{
        new TransactionTemplate(manager).executeWithoutResult(status->{
            email.sendEmail("recipient@homehub.example","Test","Committed body");
            assertEquals(1,repository.count());
            verifyNoInteractions(kafka);
        });
        assertEquals(1,repository.count());
        AuthOutboxEvent event=repository.findAll().get(0);
        assertNull(event.getPublishedAt());
        var payload=new ObjectMapper().readTree(event.getPayload());
        assertEquals("EMAIL",payload.get("eventType").asText());
        assertEquals(1,payload.get("schemaVersion").asInt());
        assertEquals("recipient@homehub.example",payload.get("email").asText());
        assertEquals("Test",payload.get("subject").asText());
        assertEquals("Committed body",payload.get("message").asText());
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.completedFuture(null));
        outbox.publish();
        verify(kafka).send(event.getTopic(),event.getEventKey(),event.getPayload());
        assertNotNull(repository.findById(event.getId()).orElseThrow().getPublishedAt());
    }

    @Test
    void multipleEmailsRunConcurrently()throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(3);
        CountDownLatch ready=new CountDownLatch(3);
        CountDownLatch start=new CountDownLatch(1);
        try{
            var sends=java.util.stream.IntStream.range(0,3).mapToObj(i->workers.submit(()->{
                ready.countDown();
                assertTrue(start.await(10,TimeUnit.SECONDS));
                new TransactionTemplate(manager).executeWithoutResult(status->
                        email.sendEmail("recipient"+i+"@homehub.example","Test "+i,"Concurrent body"));
                return null;
            })).toList();
            assertTrue(ready.await(10,TimeUnit.SECONDS));
            start.countDown();
            for(Future<?> send:sends){send.get(15,TimeUnit.SECONDS);}
            assertEquals(3,repository.count());
            assertEquals(3,repository.findAll().stream().map(AuthOutboxEvent::getId).distinct().count());
            assertTrue(repository.findAll().stream().allMatch(event->event.getPublishedAt()==null));
            verifyNoInteractions(kafka);
        }finally{start.countDown();workers.shutdownNow();}
    }
}
