
package com.ga.HomeHub;

import com.ga.HomeHub.config.ExecutorConfig;
import com.ga.HomeHub.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(EmailConcurrencyTest.TestConfig.class)
public class EmailConcurrencyTest{

    @Autowired
    private EmailService emailService;

    @Autowired
    private JavaMailSender sender;

    //Load the real executor and enable Spring asynchronous methods.
    @Configuration
    @EnableAsync
    @Import(ExecutorConfig.class)
    static class TestConfig{

        @Bean
        JavaMailSender mailSender(){
            return mock(JavaMailSender.class);
        }

        @Bean
        EmailService emailService(JavaMailSender sender,@Lazy EmailService self){
            EmailService service=new EmailService(sender,self);
            ReflectionTestUtils.setField(service,"from","test@homehub.com");
            return service;
        }
    }

    @Test
    @DisplayName("Email is sent using a background HomeHub thread")
    void emailRunsOnBackgroundThread() throws Exception{
        reset(sender);

        CountDownLatch sent=new CountDownLatch(1);
        String callingThread=Thread.currentThread().getName();
        String[] emailThread=new String[1];

        //Capture the thread that performs the email delivery.
        doAnswer(invocation->{
            emailThread[0]=Thread.currentThread().getName();
            sent.countDown();
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));

        emailService.sendEmail("user@gmail.com","Test","Hello");

        assertTrue(sent.await(5,TimeUnit.SECONDS));
        assertNotEquals(callingThread,emailThread[0]);
        assertTrue(emailThread[0].startsWith("HomeHub-"));
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Three emails are processed concurrently")
    void multipleEmailsRunConcurrently() throws Exception{
        reset(sender);

        CountDownLatch started=new CountDownLatch(3);
        CountDownLatch release=new CountDownLatch(1);
        CountDownLatch completed=new CountDownLatch(3);
        Set<String> threads=ConcurrentHashMap.newKeySet();
        AtomicInteger sentCount=new AtomicInteger();

        //Hold each email task until all three have started.
        doAnswer(invocation->{
            threads.add(Thread.currentThread().getName());
            started.countDown();

            try{
                if(!release.await(5,TimeUnit.SECONDS)){
                    throw new IllegalStateException("Timed out waiting for release");
                }
                sentCount.incrementAndGet();
            }finally{
                completed.countDown();
            }

            return null;
        }).when(sender).send(any(SimpleMailMessage.class));

        try{
            emailService.sendEmail("user1@gmail.com","Test 1","Hello");
            emailService.sendEmail("user2@gmail.com","Test 2","Hello");
            emailService.sendEmail("user3@gmail.com","Test 3","Hello");

            //All three tasks must reach the sender before any is released.
            assertTrue(started.await(5,TimeUnit.SECONDS));
        }finally{
            release.countDown();
        }

        assertTrue(completed.await(5,TimeUnit.SECONDS));
        assertEquals(3,sentCount.get());
        assertEquals(3,threads.size());
        assertTrue(threads.stream().allMatch(name->name.startsWith("HomeHub-")));

        verify(sender,times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Email is not sent when a transaction rolls back")
    void emailIsNotSentAfterRollback() throws Exception{
        reset(sender);

        //Simulate an active transaction and its synchronization callbacks.
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try{
            emailService.sendEmail("user@gmail.com","Test","Hello");

            assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());

            //Simulate transaction rollback without calling afterCommit().
            for(TransactionSynchronization synchronization:
                    TransactionSynchronizationManager.getSynchronizations()){
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        }finally{
            TransactionSynchronizationManager.clear();
        }

        verify(sender,never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Email is dispatched after a successful transaction commit")
    void emailIsSentAfterCommit() throws Exception{
        reset(sender);

        CountDownLatch sent=new CountDownLatch(1);

        doAnswer(invocation->{
            sent.countDown();
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try{
            emailService.sendEmail("user@gmail.com","Test","Hello");

            //Before commit, no email task should have been dispatched.
            verify(sender,never()).send(any(SimpleMailMessage.class));

            //Simulate a successful transaction commit.
            for(TransactionSynchronization synchronization:
                    TransactionSynchronizationManager.getSynchronizations()){
                synchronization.afterCommit();
            }
        }finally{
            TransactionSynchronizationManager.clear();
        }

        assertTrue(sent.await(5,TimeUnit.SECONDS));
        verify(sender).send(any(SimpleMailMessage.class));
    }
}
