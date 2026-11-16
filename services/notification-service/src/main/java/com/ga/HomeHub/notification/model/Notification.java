package com.ga.HomeHub.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name="notification")
@Data
public class Notification extends BaseEntity{
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(name="recipient_id",nullable=false)
    private Long recipientId;

    @Column(nullable=false,length=3000)
    private String message;

    @Column(name="is_read",nullable=false)
    private boolean isRead=false;

    @Column(name="event_type")
    private String eventType="booking";
}