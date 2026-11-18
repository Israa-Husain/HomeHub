package com.ga.HomeHub.auth.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthOutboxService {
    private static final Logger logger = LoggerFactory.getLogger(AuthOutboxService.class);

    private final AuthOutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${homehub.events.topic:homehub.notifications.v1}")
    private String topic;

    //Save the event in the caller transaction so account changes and delivery work roll back together.
    @Transactional
    public void enqueue(String type, Long recipientId, String email, String subject, String message) {
        AuthOutboxEvent event = new AuthOutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTopic(topic);
        event.setEventKey(recipientId == null ? email : String.valueOf(recipientId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getId());
        payload.put("schemaVersion", 1);
        payload.put("eventType", type);

        payload.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());

        payload.put("recipientId", recipientId);
        payload.put("email", email);
        payload.put("subject", subject);
        payload.put("message", message);

        try {
            event.setPayload(mapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize event", e);
        }

        repository.save(event);
    }


    //Publish locked events and record Kafka ack. Consumers must deduplicate crash-related redelivery.
    @Scheduled(fixedDelayString = "${homehub.outbox.delay-ms:2000}")
    @Transactional
    public void publish() {
        for (AuthOutboxEvent event : repository.lockPending()) {
            try {
                kafka.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(10, TimeUnit.SECONDS);
                event.setPublishedAt(LocalDateTime.now());
                logger.info("Published eventId={} topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                logger.warn("Publication deferred eventId={} attempts={}", event.getId(), event.getAttempts());

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
    }
}