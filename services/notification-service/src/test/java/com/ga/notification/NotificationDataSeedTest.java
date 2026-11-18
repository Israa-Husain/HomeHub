package com.ga.notification;

import com.ga.HomeHub.notification.repository.EventReceiptRepository;
import com.ga.HomeHub.notification.repository.NotificationRepository;
import com.ga.HomeHub.notification.seed.NotificationDataSeed;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationDataSeedTest{
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final EventReceiptRepository receipts=mock(EventReceiptRepository.class);
    private final NotificationRepository notifications=mock(NotificationRepository.class);
    private static final String LOOKUP="select id from users where email_address=? and status='ACTIVE' and email_verified=true";

    @Test
    void seedRequiresExplicitOptIn(){
        ConditionalOnProperty condition=NotificationDataSeed.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals(List.of("homehub.seed.enabled"),List.of(condition.name()));
        assertEquals("true",condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }

    @Test
    void missingRecipientsCannotProduceAnySeedWrites(){
        assertThrows(IllegalArgumentException.class,()->seed(" ").run());
        verifyNoInteractions(jdbc,receipts,notifications);
    }

    @Test
    void onlyExistingActiveVerifiedRecipientsCanReceiveSeedHistory(){
        when(jdbc.queryForObject(LOOKUP,Long.class,"recipient@homehub.example")).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->seed("recipient@homehub.example").run());
        verify(jdbc).queryForObject(LOOKUP,Long.class,"recipient@homehub.example");
        verifyNoInteractions(receipts,notifications);
    }

    @Test
    void initializationLocksBeforeReadingOrInserting(){
        when(jdbc.queryForObject(LOOKUP,Long.class,"recipient@homehub.example")).thenReturn(1L);
        when(receipts.claim("seed-v1-1","notification-demo-v1")).thenReturn(true);
        seed(" RECIPIENT@HOMEHUB.EXAMPLE ").run();
        var order=inOrder(jdbc,receipts,notifications);
        order.verify(jdbc).execute("select pg_advisory_xact_lock(70448423004)");
        order.verify(jdbc).queryForObject(LOOKUP,Long.class,"recipient@homehub.example");
        order.verify(receipts).claim("seed-v1-1","notification-demo-v1");
        order.verify(notifications).insert(1L,"Demo: your booking was confirmed","booking");
        order.verify(notifications).insert(1L,"Demo: an appointment reminder is available","booking");
    }

    @Test
    void seedHistoryKeepsRecipientRelationships(){
        when(jdbc.queryForObject(LOOKUP,Long.class,"first@homehub.example")).thenReturn(1L);
        when(jdbc.queryForObject(LOOKUP,Long.class,"second@homehub.example")).thenReturn(2L);
        when(receipts.claim(anyString(),anyString())).thenReturn(true);
        seed("first@homehub.example,second@homehub.example").run();
        for(long id:List.of(1L,2L)){
            verify(receipts).claim("seed-v1-"+id,"notification-demo-v1");
            verify(notifications).insert(id,"Demo: your booking was confirmed","booking");
            verify(notifications).insert(id,"Demo: an appointment reminder is available","booking");
        }
        verify(notifications,times(4)).insert(anyLong(),anyString(),anyString());
    }

    @Test
    void restartDoesNotDuplicateOrRepairExistingHistory(){
        when(jdbc.queryForObject(LOOKUP,Long.class,"recipient@homehub.example")).thenReturn(1L);
        when(receipts.claim("seed-v1-1","notification-demo-v1")).thenReturn(true,false);
        NotificationDataSeed seed=seed("recipient@homehub.example");
        seed.run();
        seed.run();
        verify(notifications,times(2)).insert(anyLong(),anyString(),anyString());
        verify(receipts,times(2)).claim("seed-v1-1","notification-demo-v1");
    }

    @Test
    void failedPersistenceRollsBackAtTransactionalBoundary()throws Exception{
        when(jdbc.queryForObject(LOOKUP,Long.class,"recipient@homehub.example")).thenReturn(1L);
        when(receipts.claim("seed-v1-1","notification-demo-v1")).thenReturn(true);
        doThrow(new IllegalStateException("Fixture persistence failure")).when(notifications).insert(anyLong(),anyString(),anyString());
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status=new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        ProxyFactory factory=new ProxyFactory(seed("recipient@homehub.example"));
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        CommandLineRunner transactional=(CommandLineRunner)factory.getProxy();
        assertThrows(IllegalStateException.class,()->transactional.run());
        verify(manager).rollback(status);
        verify(manager,never()).commit(any());
    }

    private NotificationDataSeed seed(String emails){
        return new NotificationDataSeed(jdbc,receipts,notifications,emails);
    }
}
