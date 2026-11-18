package com.ga.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ga.HomeHub.notification.dto.NotificationEvent;
import com.ga.HomeHub.notification.messaging.NotificationConsumer;
import com.ga.HomeHub.notification.repository.EventReceiptRepository;
import com.ga.HomeHub.notification.repository.NotificationRepository;
import com.ga.HomeHub.notification.service.EmailService;
import com.ga.HomeHub.notification.service.EventProcessingService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailConcurrencyTest{
    @Mock private JavaMailSender sender;
    @Mock private EventReceiptRepository receipts;
    @Mock private NotificationRepository notifications;
    private NotificationConsumer consumer;

    @BeforeEach
    void setUp(){
        EmailService email=new EmailService(sender,true,"junit-sender@homehub.example");
        consumer=new NotificationConsumer(new EventProcessingService(receipts,notifications,email));
        when(receipts.claim(anyString(),anyString())).thenReturn(true);
    }

    @Test
    void emailRunsOnConsumingThread()throws Exception{
        String callingThread=Thread.currentThread().getName();
        AtomicReference<String> deliveryThread=new AtomicReference<>();
        doAnswer(call->{deliveryThread.set(Thread.currentThread().getName());return null;})
                .when(sender).send(any(SimpleMailMessage.class));
        consumer.receive(payload(1));
        assertEquals(callingThread,deliveryThread.get());
        verify(sender).send(any(SimpleMailMessage.class));
        verify(receipts).claim(anyString(),anyString());
        verifyNoInteractions(notifications);
    }

    @Test
    void multipleEmailsRunConcurrently()throws Exception{
        CountDownLatch started=new CountDownLatch(3);
        CountDownLatch release=new CountDownLatch(1);
        CountDownLatch completed=new CountDownLatch(3);
        Set<String> threads=ConcurrentHashMap.newKeySet();
        AtomicInteger sentCount=new AtomicInteger();
        doAnswer(call->{
            threads.add(Thread.currentThread().getName());
            started.countDown();
            try{
                assertTrue(release.await(10,TimeUnit.SECONDS));
                sentCount.incrementAndGet();
            }finally{completed.countDown();}
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        ExecutorService workers=Executors.newFixedThreadPool(3);
        try{
            List<String> payloads=List.of(payload(1),payload(2),payload(3));
            var sends=payloads.stream().map(payload->workers.submit(()->consumer.receive(payload))).toList();
            assertTrue(started.await(10,TimeUnit.SECONDS));
            release.countDown();
            for(Future<?> send:sends){send.get(15,TimeUnit.SECONDS);}
            assertTrue(completed.await(10,TimeUnit.SECONDS));
            assertEquals(3,sentCount.get());
            assertEquals(3,threads.size());
            verify(sender,times(3)).send(any(SimpleMailMessage.class));
            verify(receipts,times(3)).claim(anyString(),anyString());
            verifyNoInteractions(notifications);
        }finally{release.countDown();workers.shutdownNow();}
    }

    private String payload(int index)throws Exception{
        NotificationEvent event=new NotificationEvent(UUID.randomUUID().toString(),1,"EMAIL",
                OffsetDateTime.now().toString(),null,"recipient"+index+"@homehub.example","Test "+index,"Hello");
        return new ObjectMapper().writeValueAsString(event);
    }
}
