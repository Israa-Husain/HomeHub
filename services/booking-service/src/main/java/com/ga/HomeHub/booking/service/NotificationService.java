package com.ga.HomeHub.booking.service;

import com.ga.HomeHub.booking.messaging.BookingOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService{
    private final BookingOutboxService outbox;

    //Queue delivery in the caller transaction for Notification to process.
    @Transactional
    public void sendNotifications(Long userId,String message){
        outbox.enqueue("NOTIFICATION",userId,null,"HomeHub",message);
    }
}