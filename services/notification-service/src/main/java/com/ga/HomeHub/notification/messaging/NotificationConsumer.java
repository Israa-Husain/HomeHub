package com.ga.HomeHub.notification.messaging;

import com.ga.HomeHub.notification.dto.NotificationEvent;
import com.ga.HomeHub.notification.service.EventProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {
    private final EventProcessingService processing;

    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public NotificationConsumer(EventProcessingService processing) {
        this.processing = processing;
    }

    @KafkaListener(topics = "${homehub.events.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void receive(String payload) {
        if (payload == null || payload.length() > 200000) {
            throw new IllegalArgumentException("Invalid event payload size");
        }

        NotificationEvent event;

        try {
            event = mapper.readValue(payload, NotificationEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed notification event");
        }

        if (event == null) {
            throw new IllegalArgumentException("Event body is required");
        }

        processing.process(event);
    }
}