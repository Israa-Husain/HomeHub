package com.ga.booking.cucumber;

import com.ga.HomeHub.booking.BookingServiceApplication;
import com.ga.HomeHub.booking.client.AuthServiceClient;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CucumberContextConfiguration
@ActiveProfiles("cucumber")
@SpringBootTest(
        classes=BookingServiceApplication.class,
        webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={
                "spring.datasource.url=jdbc:h2:mem:homehub-booking-cucumber;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.hikari.schema=PUBLIC",

                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=false",
                "spring.sql.init.mode=never",

                "homehub.seed.enabled=false",
                "homehub.cache.enabled=false",
                "homehub.scheduling.enabled=false",

                "homehub.auth-url=http://localhost:1",
                "homehub.internal-key=cucumber-only-internal-key-1234567890",
                "jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",

                "spring.kafka.bootstrap-servers=localhost:1",
                "spring.kafka.admin.auto-create=false",
                "spring.kafka.listener.auto-startup=false",
                "homehub.events.topic=homehub.notifications.v1",

                "homehub.booking.normal-notice-hours=24",
                "homehub.booking.emergency-notice-hours=1",
                "homehub.booking.emergency-surcharge=0.20",
                "homehub.booking.reschedule-notice-hours=24",
                "homehub.booking.response-deadline-hours=24",
                "homehub.booking.cancellation-notice-hours=24",
                "homehub.booking.waitlist-offer-minutes=15",
                "homehub.booking.report-deadline-hours=24",

                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"
        }
)
@Import({ScenarioState.class,BookingTestData.class})
public class CucumberSpringConfiguration{

    @MockitoBean
    private AuthServiceClient auth;

    @MockitoBean
    private KafkaTemplate<String,String> kafka;

    //Prevent background jobs from changing scenario fixtures.
    @MockitoBean
    private TaskScheduler taskScheduler;
}