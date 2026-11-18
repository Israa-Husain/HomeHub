package com.ga.HomeHub.notification.service;

import com.ga.HomeHub.notification.dto.NotificationEvent;
import com.ga.HomeHub.notification.repository.EventReceiptRepository;
import com.ga.HomeHub.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class EventProcessingService{
    private final EventReceiptRepository receipts;
    private final NotificationRepository notifications;
    private final EmailService email;
    private final ObjectMapper mapper=new ObjectMapper();

    public EventProcessingService(
            EventReceiptRepository receipts,
            NotificationRepository notifications,
            EmailService email
    ){
        this.receipts=receipts;
        this.notifications=notifications;
        this.email=email;
    }

    //The receipt and JPA history insert commit together, before the listener returns to Kafka.
    @Transactional
    public void process(NotificationEvent event){
        event.validate();

        if(!receipts.claim(event.eventId(),fingerprint(event))){
            return;
        }

        if("EMAIL".equals(event.eventType())){
            //SMTP cannot commit atomically with the database. A crash can still cause duplicate email.
            email.send(event);
        }else{
            notifications.insert(
                    event.recipientId(),
                    event.message(),
                    "booking"
            );
        }
    }

    private String fingerprint(NotificationEvent event){
        try{
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(mapper.writeValueAsBytes(event))
            );
        }catch(JsonProcessingException | NoSuchAlgorithmException e){
            throw new IllegalStateException(
                    "Cannot fingerprint notification event",
                    e
            );
        }
    }
}