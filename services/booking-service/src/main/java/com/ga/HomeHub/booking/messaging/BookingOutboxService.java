package com.ga.HomeHub.booking.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
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
public class BookingOutboxService{
    private static final Logger logger=LoggerFactory.getLogger(BookingOutboxService.class);
    private final BookingOutboxRepository repository;
    private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper mapper=new ObjectMapper();

    @Value("${homehub.events.topic:homehub.notifications.v1}")
    private String topic;

    //Joins the caller transaction so a rollback removes the booking change and its event.
    @Transactional
    public void enqueue(String type,Long recipientId,String email,String subject,String message){
        if((recipientId==null && (email==null || email.isBlank())) || message==null){
            throw new IllegalArgumentException("An event recipient and message are required");
        }
        BookingOutboxEvent event=new BookingOutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTopic(topic);
        event.setEventKey(recipientId==null?email:recipientId.toString());
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("eventId",event.getId());
        payload.put("schemaVersion",1);
        payload.put("eventType",type);
        payload.put("occurredAt",OffsetDateTime.now(ZoneOffset.UTC).toString());
        payload.put("recipientId",recipientId);
        payload.put("email",email);
        payload.put("subject",subject);
        payload.put("message",message);
        try{
            event.setPayload(mapper.writeValueAsString(payload));
        }catch(JsonProcessingException e){
            throw new IllegalStateException("Cannot serialize notification event",e);
        }
        repository.save(event);
    }


    //Publish through the transaction proxy with at-least-once delivery and consumer-side deduplication.
    @Scheduled(fixedDelayString="${homehub.outbox.delay-ms:2000}")
    @Transactional
    public void publish(){
        for(BookingOutboxEvent event:repository.lockPending()){
            try{
                kafka.send(event.getTopic(),event.getEventKey(),event.getPayload()).get(10,TimeUnit.SECONDS);
                event.setPublishedAt(LocalDateTime.now());
            }catch(InterruptedException e){
                event.setAttempts(event.getAttempts()+1);
                Thread.currentThread().interrupt();
                logger.warn("Outbox publication interrupted eventId={}",event.getId());
                break;
            }catch(Exception e){
                event.setAttempts(event.getAttempts()+1);
                logger.warn("Outbox publication deferred eventId={} attempts={}",event.getId(),event.getAttempts());
                break;
            }
        }
    }
}