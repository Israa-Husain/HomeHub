package com.ga.booking;

import com.ga.HomeHub.booking.BookingServiceApplication;
import com.ga.HomeHub.booking.client.AuthServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes=BookingServiceApplication.class,properties={
        "spring.datasource.url=jdbc:h2:mem:junit-booking-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
        "homehub.cache.enabled=false",
        "homehub.auth-url=http://localhost:1"
})
class BookingServiceApplicationTests{
    @MockitoBean private KafkaTemplate<String,String> kafka;
    @MockitoBean private KafkaAdmin kafkaAdmin;
    @MockitoBean private TaskScheduler scheduler;
    @MockitoBean private AuthServiceClient auth;

    @Test
    void contextLoads(){
    }
}
