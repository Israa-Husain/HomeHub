package com.ga.notification.cucumber;

import com.ga.HomeHub.notification.NotificationServiceApplication;
import com.ga.HomeHub.notification.client.AuthServiceClient;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CucumberContextConfiguration
@ActiveProfiles("cucumber")
@EmbeddedKafka(
        partitions=3,
        topics={
                "homehub.notifications.v1",
                "homehub.notifications.v1.DLT"
        },
        bootstrapServersProperty="spring.kafka.bootstrap-servers",
        brokerProperties={
                "group.initial.rebalance.delay.ms=0"
        }
)
@SpringBootTest(
        classes=NotificationServiceApplication.class,
        webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={
                "spring.datasource.url=jdbc:h2:mem:homehub-notification-cucumber;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.hikari.schema=PUBLIC",

                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:cucumber-schema.sql",
                "spring.flyway.enabled=false",
                "homehub.seed.enabled=false",

                "jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "homehub.auth-url=http://localhost:1",

                "homehub.events.topic=homehub.notifications.v1",
                "homehub.events.dlt=homehub.notifications.v1.DLT",

                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=homehub-notification-cucumber",
                "spring.kafka.consumer.enable-auto-commit=false",
                "spring.kafka.consumer.auto-offset-reset=earliest",

                "spring.kafka.listener.ack-mode=record",
                "spring.kafka.listener.concurrency=1",
                "spring.kafka.listener.auto-startup=true",
                "spring.kafka.admin.auto-create=false",

                "spring.kafka.producer.properties.delivery.timeout.ms=10000",
                "spring.kafka.producer.properties.request.timeout.ms=5000",

                "homehub.mail.enabled=true",
                "homehub.mail.from=cucumber-sender@homehub.example",
                "spring.mail.host=localhost",
                "spring.mail.port=1",
                "spring.mail.username=",
                "spring.mail.password=",
                "management.health.mail.enabled=false",

                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"
        }
)
@Import({ScenarioState.class,NotificationTestData.class})
public class CucumberSpringConfiguration{

    @MockitoBean
    private AuthServiceClient auth;

    @MockitoBean
    private JavaMailSender sender;

    //Prevent scheduled background work during event scenarios.
    @MockitoBean
    private TaskScheduler scheduler;
}