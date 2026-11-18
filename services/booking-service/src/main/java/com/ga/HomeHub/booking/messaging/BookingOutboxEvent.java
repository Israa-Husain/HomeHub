package com.ga.HomeHub.booking.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="outbox_event")
@Getter
@Setter
public class BookingOutboxEvent{
    @Id
    private String id;

    @Column(nullable=false)
    private String topic;

    @Column(name="event_key",nullable=false)
    private String eventKey;

    @Column(nullable=false,columnDefinition="text")
    private String payload;

    @Column(name="created_at",nullable=false)
    private LocalDateTime createdAt=LocalDateTime.now();

    @Column(name="published_at")
    private LocalDateTime publishedAt;

    @Column(nullable=false)
    private int attempts;
}