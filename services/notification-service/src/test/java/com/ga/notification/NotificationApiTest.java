package com.ga.notification;

import com.ga.HomeHub.notification.controller.NotificationController;
import com.ga.HomeHub.notification.exception.ApiExceptionHandler;
import com.ga.HomeHub.notification.model.Notification;
import com.ga.HomeHub.notification.repository.NotificationRepository;
import com.ga.HomeHub.notification.security.MyUserDetails;
import com.ga.HomeHub.notification.service.AccountVerificationService;
import com.ga.HomeHub.notification.service.CurrentUserService;
import com.ga.HomeHub.notification.service.LiveNotifications;
import com.ga.HomeHub.notification.service.NotificationService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.AsyncContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(NotificationApiTest.Config.class)
class NotificationApiTest{
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=NotificationRepository.class)
    static class Config{
        @Bean
        DataSource dataSource(){
            return new DriverManagerDataSource("jdbc:h2:mem:junit-history-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        }
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source){
            LocalContainerEntityManagerFactoryBean factory=new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan(Notification.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop"));
            return factory;
        }
        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
        @Bean
        CurrentUserService current(){return mock(CurrentUserService.class);}
        @Bean
        AccountVerificationService accounts(){return mock(AccountVerificationService.class);}
        @Bean
        LiveNotifications live(NotificationRepository repository,AccountVerificationService accounts){
            return new LiveNotifications(repository,accounts,Runnable::run,2);
        }
        @Bean
        NotificationService notifications(NotificationRepository repository,LiveNotifications live){
            return new NotificationService(repository,live);
        }
    }

    @Autowired private NotificationRepository repository;
    @Autowired private CurrentUserService current;
    @Autowired private AccountVerificationService accounts;
    @Autowired private LiveNotifications live;
    @Autowired private NotificationService service;
    private NotificationService notifications;
    private MockMvc mvc;

    @BeforeEach
    void setUp(){
        repository.deleteAll();
        reset(current,accounts);
        notifications=service;
        mvc=MockMvcBuilders.standaloneSetup(new NotificationController(notifications,current))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void historyCountsAndReadUpdatesUseCurrentRecipientForEveryRole()throws Exception{
        for(String role:List.of("HOMEOWNER","PROVIDER","ADMIN")){
            long id=List.of("HOMEOWNER","PROVIDER","ADMIN").indexOf(role)+1L;
            MyUserDetails user=new MyUserDetails(id,"role"+id+"@homehub.example",role);
            Notification item=notification(id,"Booking update");
            when(current.getCurrentUser()).thenReturn(user);
            mvc.perform(get("/api/notifications")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].message").value("Booking update"))
                    .andExpect(jsonPath("$[0].read").value(false))
                    .andExpect(jsonPath("$[0].recipient").doesNotExist());
            mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(1));
            mvc.perform(patch("/api/notifications/"+item.getId()+"/read")).andExpect(status().isOk());
            assertTrue(repository.findById(item.getId()).orElseThrow().isRead());
            mvc.perform(get("/api/notifications")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].read").value(true));
            mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));
        }
    }

    @Test
    void anotherRecipientsNotificationCannotBeMarkedRead()throws Exception{
        Notification other=notification(2L,"Other account");
        when(current.getCurrentUser()).thenReturn(new MyUserDetails(1L,"owner@homehub.example","HOMEOWNER"));
        mvc.perform(patch("/api/notifications/"+other.getId()+"/read")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notification not found"));
        assertFalse(repository.findById(other.getId()).orElseThrow().isRead());
    }

    @Test
    void realSseEmitterDeliversExistingNamedEventAndCleansUpOnCompletion()throws Exception{
        MyUserDetails user=new MyUserDetails(1L,"private@example.test","HOMEOWNER");
        when(current.getCurrentUser()).thenReturn(user);
        when(accounts.verify("Bearer junit-stream-token")).thenReturn(user);
        var connection=mvc.perform(get("/api/notifications/stream")
                        .header("Authorization","Bearer junit-stream-token").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        AsyncContext async=connection.getRequest().getAsyncContext();
        Map<?,?> clients=(Map<?,?>)ReflectionTestUtils.getField(live,"clients");
        assertNotNull(clients);
        SseEmitter stream=(SseEmitter)ReflectionTestUtils.getField(((List<?>)clients.get(1L)).get(0),"emitter");
        assertNotNull(stream);
        try{
            Notification item=notification(1L,"Booking update");
            notification(2L,"Other account");
            live.poll();
            String body=connection.getResponse().getContentAsString();
            assertTrue(connection.getResponse().getContentType().startsWith("text/event-stream"));
            assertTrue(body.contains("event:booking"));
            assertTrue(body.contains("id:"+item.getId()));
            assertTrue(body.contains("\"message\":\"Booking update\""));
            assertTrue(body.contains("event:refresh"));
            assertFalse(body.contains("private@example.test"));
            assertFalse(body.contains("Other account"));
        }finally{
            stream.complete();
            async.complete();
        }
        assertTrue(clients.isEmpty());
    }

    private Notification notification(long recipient,String message){
        Notification item=new Notification();
        item.setRecipientId(recipient);
        item.setMessage(message);
        return repository.saveAndFlush(item);
    }
}
