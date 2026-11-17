package com.ga.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ga.HomeHub.booking.client.AccountDirectory;
import com.ga.HomeHub.booking.config.BookingPolicyProperties;
import com.ga.HomeHub.booking.messaging.BookingOutboxEvent;
import com.ga.HomeHub.booking.messaging.BookingOutboxRepository;
import com.ga.HomeHub.booking.messaging.BookingOutboxService;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.BookingRescheduleRequest;
import com.ga.HomeHub.booking.model.ProviderProfile;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.repository.AvailabilityRepository;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.BookingRescheduleRequestRepository;
import com.ga.HomeHub.booking.repository.BookingWaitlistRepository;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import com.ga.HomeHub.booking.service.AuditLogService;
import com.ga.HomeHub.booking.service.BookingRescheduleService;
import com.ga.HomeHub.booking.service.BookingWaitlistService;
import com.ga.HomeHub.booking.service.CurrentUserService;
import com.ga.HomeHub.booking.service.EmailService;
import com.ga.HomeHub.booking.service.NotificationService;
import com.ga.HomeHub.booking.validation.ProviderScheduleValidator;
import com.ga.HomeHub.booking.validation.ServiceEligibilityValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
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
import org.mockito.ArgumentMatchers;
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

@SpringJUnitConfig(BookingOutboxTransactionTest.Config.class)
class BookingOutboxTransactionTest{
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=BookingOutboxRepository.class)
    static class Config{
        @Bean
        DataSource dataSource(){
            return new DriverManagerDataSource("jdbc:h2:mem:junit-outbox-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        }
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source){
            LocalContainerEntityManagerFactoryBean factory=new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan(BookingOutboxEvent.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop"));
            return factory;
        }
        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
        @Bean
        KafkaTemplate<String,String> kafka(){return mock(KafkaTemplate.class);}
        @Bean
        BookingOutboxService outbox(BookingOutboxRepository repository,KafkaTemplate<String,String> kafka){
            BookingOutboxService service=new BookingOutboxService(repository,kafka);
            ReflectionTestUtils.setField(service,"topic","junit-only-notifications");
            return service;
        }
        @Bean
        EmailService email(BookingOutboxService outbox){return new EmailService(outbox);}
    }

    @Autowired private EmailService email;
    @Autowired private BookingOutboxService outbox;
    @Autowired private BookingOutboxRepository repository;
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
        BookingOutboxEvent event=repository.findAll().get(0);
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
            assertEquals(3,repository.findAll().stream().map(BookingOutboxEvent::getId).distinct().count());
            assertTrue(repository.findAll().stream().allMatch(event->event.getPublishedAt()==null));
            verifyNoInteractions(kafka);
        }finally{start.countDown();workers.shutdownNow();}
    }

    @Test
    void requestUsesPersistentNotificationAndQueuesDeliveryOnlyAfterCommit()throws Exception{
        BookingRepository bookings=mock(BookingRepository.class);
        BookingRescheduleRequestRepository requests=mock(BookingRescheduleRequestRepository.class);
        ProviderProfileRepository providers=mock(ProviderProfileRepository.class);
        CurrentUserService current=mock(CurrentUserService.class);
        EntityManager em=mock(EntityManager.class);
        User homeowner=new User();homeowner.setId(1L);
        User providerUser=new User();providerUser.setId(2L);providerUser.setRole(Role.PROVIDER);
        ProviderProfile provider=new ProviderProfile();provider.setId(3L);provider.setUser(providerUser);
        ServiceOffering offering=new ServiceOffering();offering.setProvider(provider);
        Booking booking=new Booking();booking.setId(6L);booking.setCustomer(homeowner);booking.setService(offering);
        booking.setStatus(BookingStatus.CONFIRMED);booking.setBookingDate(LocalDate.now().plusDays(3));booking.setStartTime(LocalTime.of(10,0));
        when(bookings.findById(6L)).thenReturn(Optional.of(booking));
        when(current.getCurrentUser()).thenReturn(homeowner);
        when(providers.lockById(3L)).thenReturn(Optional.of(provider));
        when(requests.save(ArgumentMatchers.any())).thenAnswer(call->{
            BookingRescheduleRequest request=call.getArgument(0);request.setId(7L);return request;
        });
        ProviderScheduleValidator schedule=new ProviderScheduleValidator(mock(AvailabilityRepository.class),bookings,mock(BookingWaitlistRepository.class));
        ServiceEligibilityValidator eligibility=new ServiceEligibilityValidator(em,mock(AccountDirectory.class));
        BookingRescheduleService reschedules=new BookingRescheduleService(schedule,new BookingPolicyProperties(),eligibility,
                requests,bookings,providers,current,mock(AuditLogService.class),mock(BookingWaitlistService.class),em,new NotificationService(outbox));
        ExecutorService publisher=Executors.newSingleThreadExecutor();
        try{
            new TransactionTemplate(manager).executeWithoutResult(status->{
                reschedules.request(6L,LocalDate.now().plusDays(4),LocalTime.of(11,0));
                assertEquals(1,repository.count());
                try{publisher.submit(outbox::publish).get(10,TimeUnit.SECONDS);}
                catch(Exception e){throw new IllegalStateException("Concurrent publisher failed",e);}
                verifyNoInteractions(kafka);
            });
            BookingOutboxEvent event=repository.findAll().get(0);
            var payload=new ObjectMapper().readTree(event.getPayload());
            assertEquals("NOTIFICATION",payload.get("eventType").asText());
            assertEquals(2L,payload.get("recipientId").asLong());
            assertTrue(payload.get("message").asText().contains("Reschedule request #7"));
            assertNull(event.getPublishedAt());
            when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.completedFuture(null));
            outbox.publish();
            verify(kafka).send(event.getTopic(),event.getEventKey(),event.getPayload());
            assertNotNull(repository.findById(event.getId()).orElseThrow().getPublishedAt());
        }finally{publisher.shutdownNow();}
    }
}
