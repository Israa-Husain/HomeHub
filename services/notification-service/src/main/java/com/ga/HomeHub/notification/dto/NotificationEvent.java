package com.ga.HomeHub.notification.dto;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

//The version-one wire contract matches both Auth and Booking outboxes.
public record NotificationEvent(
        String eventId,
        int schemaVersion,
        String eventType,
        String occurredAt,
        Long recipientId,
        String email,
        String subject,
        String message
){
    public void validate(){
        if(eventId==null || eventId.length()!=36){
            throw new IllegalArgumentException("A UUID event ID is required");
        }

        try{
            if(!UUID.fromString(eventId).toString().equals(eventId)){
                throw new IllegalArgumentException("Invalid event ID");
            }
        }catch(IllegalArgumentException e){
            throw new IllegalArgumentException("Invalid event ID");
        }

        if(schemaVersion!=1
                || !("EMAIL".equals(eventType) || "NOTIFICATION".equals(eventType))){
            throw new IllegalArgumentException("Unsupported notification event contract");
        }

        try{
            if(occurredAt==null){
                throw new IllegalArgumentException("Event time is required");
            }

            OffsetDateTime.parse(occurredAt);
        }catch(DateTimeParseException e){
            throw new IllegalArgumentException("Invalid event time");
        }

        if(message==null || message.isBlank() || message.length()>100000){
            throw new IllegalArgumentException("Invalid event message");
        }

        if(subject!=null
                && (subject.length()>500 || subject.contains("\r") || subject.contains("\n"))){
            throw new IllegalArgumentException("Invalid event subject");
        }

        if("NOTIFICATION".equals(eventType)
                && (recipientId==null || recipientId<=0 || message.length()>3000)){
            throw new IllegalArgumentException("Invalid notification recipient or message");
        }

        if("EMAIL".equals(eventType)
                && (email==null || email.length()>320
                || !email.matches("[^\\s@,;<>]+@[^\\s@,;<>]+\\.[^\\s@,;<>]+"))){
            throw new IllegalArgumentException("Invalid email recipient");
        }
    }
}