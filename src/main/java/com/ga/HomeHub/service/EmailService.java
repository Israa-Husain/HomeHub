
package com.ga.HomeHub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EmailService{
    private final JavaMailSender sender;
    private final EmailService emailService;
    private final Logger logger=Logger.getLogger(EmailService.class.getName());

    @Value("${spring.mail.username}")
    private String from;

    public EmailService(JavaMailSender sender,@Lazy EmailService emailService){
        this.sender=sender;
        this.emailService=emailService;
    }

    public void sendEmail(String to,String subject,String body){
        //Log email scheduling without exposing recipient details or message contents.
        logger.info("Email requested | Thread: "+Thread.currentThread().getName());

        //Send the email only after the database transaction commits successfully.
        if(TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()){
            //Log that the email must wait for the transaction to commit.
            logger.fine("Email deferred until transaction commit");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override
                public void afterCommit(){
                    //Log successful commit before dispatching the asynchronous task.
                    logger.info("Transaction committed - dispatching email asynchronously");
                    emailService.sendEmailAsync(to,subject,body);
                }
            });
        }else{
            //Log immediate dispatch when there is no active transaction.
            logger.info("No active transaction - dispatching email asynchronously");
            emailService.sendEmailAsync(to,subject,body);
        }
    }

    //Send email asynchronously using a Spring-managed thread.
    @Async("homeHubExecutor")
    public void sendEmailAsync(String to,String subject,String body){
        logger.info("Async email processing started | Thread: "+Thread.currentThread().getName());
        try{
            SimpleMailMessage message=new SimpleMailMessage();
            message.setTo(to);
            message.setFrom(from);
            message.setSubject(subject);
            message.setText(body);

            //Measure SMTP delivery time on the background thread.
            long startTime=System.nanoTime();

            try{
                sender.send(message);
                //Confirm successful SMTP submission on the background thread.
                logger.info("Email submitted successfully to SMTP server | Thread: "+Thread.currentThread().getName());
            }finally{
                long durationMs=(System.nanoTime()-startTime)/1_000_000;
                logger.info("SMTP send attempt duration: "+durationMs+" ms | Thread: "+Thread.currentThread().getName());
            }
        }catch(Exception e){
            logger.log(Level.WARNING,"Email sending failed | Thread: "+Thread.currentThread().getName(),e);
        }
    }
}
