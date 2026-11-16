package com.ga.HomeHub.auth.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
//outgoing message saved in Auth’s database before it is sent to Kafka.
public class AuthOutboxEvent {
    @Id
    private String id;

    //Kafka topic where the email will be sent.
    @Column(nullable = false)
    private String topic;

    //Kafka message key.
    @Column(nullable = false)
    private String eventKey;

    //JSON with event type, recipient, subject and message.
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    //When auth created the event.
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    //Null until Kafka acknowledges publication.
    private LocalDateTime publishedAt;

    //count failed publication attempts.
    private int attempts;
}
