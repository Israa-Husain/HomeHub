package com.ga.notification;

import com.ga.HomeHub.notification.NotificationServiceApplication;
import com.ga.HomeHub.notification.client.AuthServiceClient;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes=NotificationServiceApplication.class,properties={
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.schema=PUBLIC",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "homehub.seed.enabled=false",
        "homehub.scheduling.enabled=false",
        "spring.task.scheduling.enabled=false",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.listener.auto-startup=false",
        "homehub.events.topic=junit.notifications",
        "jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "homehub.internal-key=junit-only-internal-key-12345678901234567890",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
        "homehub.auth-url=http://localhost:1",
        "homehub.events.dlt=junit.notifications.DLT",
        "homehub.mail.enabled=false",
        "homehub.mail.from=junit@homehub.example",
        "spring.mail.host=localhost",
        "spring.mail.port=1",
        "spring.mail.username=",
        "spring.mail.password=",
        "management.health.mail.enabled=false"
})
@Import(NotificationServiceApplicationTests.IsolatedDatabase.class)
class NotificationServiceApplicationTests{
    @MockitoBean private KafkaTemplate<String,String> kafka;
    @MockitoBean private KafkaAdmin kafkaAdmin;
    @MockitoBean private TaskScheduler scheduler;
    @MockitoBean private AuthServiceClient auth;
    @MockitoBean private JavaMailSender mail;

    @Test
    void contextLoads(){
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class IsolatedDatabase{
        @Bean
        DataSource dataSource(){
            DriverManagerDataSource source=new DriverManagerDataSource(
                    "jdbc:h2:mem:junit-notification-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
            new JdbcTemplate(source).execute("create table outbox_event(id varchar(255) primary key,topic varchar(255) not null,event_key varchar(255) not null,payload text not null,created_at timestamp not null,published_at timestamp,attempts integer not null)");
            return source;
        }
    }
}
