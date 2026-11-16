package com.ga.HomeHub.notification.dto;

import com.ga.HomeHub.notification.model.Notification;

import java.time.LocalDateTime;

//Expose notification identity, content, read state, type and creation time.
public record NotificationResponse(
        Long id,
        String message,
        boolean read,
        String type,
        LocalDateTime createdAt
){
    public static NotificationResponse from(Notification notification){
        return new NotificationResponse(
                notification.getId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getEventType(),
                notification.getCreatedAt()
        );
    }
}