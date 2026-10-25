package com.ga.HomeHub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.logging.Logger;

@Service
public class EmailService{
    private final JavaMailSender sender;
    private final Logger logger=Logger.getLogger(EmailService.class.getName());
    @Value("${spring.mail.username}")
    private String from;

    public EmailService(JavaMailSender sender){
        this.sender = sender;
    }

    public void sendEmail(String to, String subject, String body){

        //Send the email only after the database transaction commits successfully.
        if(TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override
                public void afterCommit(){
                    try{
                        SimpleMailMessage message = new SimpleMailMessage();
                        message.setTo(to);
                        message.setFrom(from);
                        message.setSubject(subject);
                        message.setText(body);

                        sender.send(message);
                    }catch(Exception e){
                        logger.warning("Email delivery failed: "+e.getMessage());
                    }
                }
            });
        }else{
            try{
                SimpleMailMessage message=new SimpleMailMessage();
                message.setTo(to);
                message.setFrom(from);
                message.setSubject(subject);
                message.setText(body);

                sender.send(message);
            }catch(Exception e){
                logger.warning("Email delivery failed: "+e.getMessage());
            }
        }
    }
}