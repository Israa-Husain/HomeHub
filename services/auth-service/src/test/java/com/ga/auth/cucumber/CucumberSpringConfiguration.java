package com.ga.auth.cucumber;

import com.ga.HomeHub.auth.AuthServiceApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CucumberContextConfiguration
@ActiveProfiles("cucumber")
@SpringBootTest(classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:homehub-auth-cucumber;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.schema=PUBLIC",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "homehub.seed.enabled=false", "homehub.demo.enabled=false",
        "homehub.bootstrap.email=", "homehub.bootstrap.password=",
        "homehub.scheduling.enabled=false", "spring.task.scheduling.enabled=false",
        "homehub.outbox.delay-ms=86400000",
        "spring.kafka.admin.auto-create=false", "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:1",
        "homehub.events.topic=homehub.notifications.v1",
        "homehub.storage.bucket=", "homehub.storage.public-base=",
        "homehub.internal-key=cucumber-only-internal-key-1234567890",
        "jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt-expiration-ms=3600000", "app.base-url=http://localhost",
        "management.health.mail.enabled=false", "management.health.kafka.enabled=false",
        "springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"
})
@Import({ScenarioState.class, AuthTestData.class})
public class CucumberSpringConfiguration {
    //Email events remain in the test outbox, no broker or real email delivery is used.
    @MockitoBean
    private KafkaTemplate<String, String> kafka;
}
