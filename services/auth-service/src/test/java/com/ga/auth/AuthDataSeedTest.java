package com.ga.auth;

import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.Role;
import com.ga.HomeHub.auth.model.enums.UserStatus;
import com.ga.HomeHub.auth.repository.UserRepository;
import com.ga.HomeHub.auth.seed.AuthDataSeed;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthDataSeedTest{
    private final UserRepository users=mock(UserRepository.class);
    private final PasswordEncoder encoder=mock(PasswordEncoder.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private AuthDataSeed seed;
    private static final String EMAIL="administrator@homehub.example";
    private static final String PASSWORD="junit-only-password-123";

    @BeforeEach
    void setUp(){
        seed=new AuthDataSeed(users,encoder,jdbc);
        configure(EMAIL,PASSWORD);
    }

    @Test
    void seedRequiresExplicitOptIn(){
        ConditionalOnProperty condition=AuthDataSeed.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals(List.of("homehub.seed.enabled"),List.of(condition.name()));
        assertEquals("true",condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }

    @Test
    void normalStartupRetainsExistingBootstrapAndDoesNotInspectDemoDatabase(){
        configure(EMAIL.toUpperCase(Locale.ROOT),PASSWORD);
        when(users.existsByEmailAddress(EMAIL)).thenReturn(true);
        seed.run();
        verify(users).existsByEmailAddress(EMAIL);
        verify(users,never()).save(any());
        verifyNoInteractions(encoder);
        verify(jdbc).execute("select pg_advisory_xact_lock(70448423001)");
        verify(jdbc,never()).queryForObject("SELECT current_database()",String.class);
    }

    @Test
    void unconfiguredBootstrapDoesNotWrite(){
        configure("","");
        seed.run();
        verifyNoInteractions(users,encoder,jdbc);
    }

    @Test
    void missingOrWeakPasswordCannotProduceAnySeedWrites(){
        for(String password:List.of("","short","        ")){
            configure(EMAIL,password);
            assertThrows(IllegalArgumentException.class,()->seed.run());
        }
        configure("",PASSWORD);
        assertThrows(IllegalArgumentException.class,()->seed.run());
        verifyNoInteractions(users,encoder,jdbc);
    }

    @Test
    void initializationLocksBeforeReadingOrInserting(){
        when(encoder.encode(PASSWORD)).thenReturn("junit-encoded-password");
        seed.run();
        var order=inOrder(jdbc,users,encoder);
        order.verify(jdbc).execute("select pg_advisory_xact_lock(70448423001)");
        order.verify(users).existsByEmailAddress(EMAIL);
        order.verify(encoder).encode(PASSWORD);
        order.verify(users).save(any(User.class));
        ArgumentCaptor<User> captured=ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        User account=captured.getValue();
        assertEquals(EMAIL,account.getEmailAddress());
        assertEquals(Role.ADMIN,account.getRole());
        assertEquals(UserStatus.ACTIVE,account.getStatus());
        assertTrue(account.isEmailVerified());
        assertEquals("junit-encoded-password",account.getPasswordHash());
        assertNotEquals(PASSWORD,account.getPasswordHash());
        assertFalse(account.getFirstName().isBlank());
        assertFalse(account.getLastName().isBlank());
    }

    @Test
    void restartDoesNotDuplicateOrResetExistingAccount(){
        when(users.existsByEmailAddress(EMAIL)).thenReturn(false,true);
        when(encoder.encode(PASSWORD)).thenReturn("junit-encoded-password");
        seed.run();
        seed.run();
        verify(users,times(1)).save(any());
        verify(encoder,times(1)).encode(PASSWORD);
    }

    @Test
    void failedPersistenceRollsBackAtTransactionalBoundary()throws Exception{
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status=new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        doThrow(new IllegalStateException("Fixture persistence failure")).when(users).save(any());
        ProxyFactory factory=new ProxyFactory(seed);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        CommandLineRunner transactional=(CommandLineRunner)factory.getProxy();
        assertThrows(IllegalStateException.class,()->transactional.run());
        verify(manager).rollback(status);
        verify(manager,never()).commit(any());
    }

    private void configure(String email,String password){
        ReflectionTestUtils.setField(seed,"email",email);
        ReflectionTestUtils.setField(seed,"password",password);
    }
}
