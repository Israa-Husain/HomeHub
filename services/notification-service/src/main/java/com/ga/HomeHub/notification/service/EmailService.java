package com.ga.HomeHub.notification.service;

import com.ga.HomeHub.notification.dto.NotificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService{
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;

    public EmailService(
            JavaMailSender sender,
            @Value("${homehub.mail.enabled:false}") boolean enabled,
            @Value("${homehub.mail.from}") String from
    ){
        this.sender=sender;
        this.enabled=enabled;
        this.from=from;
    }

    public void send(NotificationEvent event){
        sendEmail(event.email(),event.subject(),event.message());
    }

    //Let mail failures propagate to Kafka's retry handler.
    public void sendEmail(String to,String subject,String body){
        if(!enabled){
            throw new IllegalStateException("Email delivery is disabled");
        }

        SimpleMailMessage mail=new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(to);
        mail.setSubject(subject);
        mail.setText(body);

        sender.send(mail);
    }
}