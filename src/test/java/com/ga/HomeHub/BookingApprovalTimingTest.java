package com.ga.HomeHub;

import com.ga.HomeHub.exception.InvalidBookingStatusException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.*;
import com.ga.HomeHub.model.enums.*;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import com.ga.HomeHub.repository.ServiceReportRepository;
import com.ga.HomeHub.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// No Spring application context, JDBC connection, scheduling or real records.
@ExtendWith(MockitoExtension.class)
class BookingApprovalTimingTest {
    @Mock private BookingRepository repository;
    @Mock private ProviderProfileRepository providerLocks;
    @Mock private CurrentUserService current;
    @Mock private EntityManager entityManager;
    @Mock private AuditLogService audit;
    @Mock private NotificationService notification;
    @Mock private EmailService email;
    @Mock private BookingWaitlistService waitlist;
    @Mock private ServiceReportRepository reports;
    @InjectMocks private BookingService service;
    private final LocalDateTime now=LocalDateTime.of(2026,10,29,9,0);
    private Booking booking;
    private ProviderProfile provider;
    private User providerUser;

    @BeforeEach
    void setUp(){
        providerUser=new User();providerUser.setId(20L);providerUser.setRole(Role.PROVIDER);
        User homeowner=new User();homeowner.setId(10L);
        provider=new ProviderProfile();provider.setId(30L);provider.setUser(providerUser);
        ServiceOffering offering=new ServiceOffering();offering.setProvider(provider);
        booking=new Booking();booking.setId(1L);booking.setService(offering);booking.setCustomer(homeowner);
        booking.setStatus(BookingStatus.PENDING);
        appointment(now.plusHours(1));
    }

    @Test
    void pastAndExactStartAreRejectedWithNullOrFutureDeadlineForBothRoles(){
        for(Role role:new Role[]{Role.PROVIDER,Role.ADMIN}){
            for(LocalDateTime start:new LocalDateTime[]{now.minusSeconds(1),now}){
                for(LocalDateTime deadline:new LocalDateTime[]{null,now.plusHours(2)}){
                    reset(repository,providerLocks,current,entityManager,audit,notification,email,waitlist,reports);
                    appointment(start);booking.setResponseDeadline(deadline);booking.setStatus(BookingStatus.PENDING);
                    authorized(role);
                    try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
                        time.when(LocalDateTime::now).thenReturn(now);
                        var failure=assertThrows(InvalidBookingStatusException.class,
                                ()->service.updateBookingStatus(1L,BookingStatus.CONFIRMED));
                        assertEquals("Cannot approve a booking whose appointment has already started",failure.getMessage());
                    }
                    unchanged();
                }
            }
        }
    }

    @Test
    void futureApprovalSucceedsWithNullOrFutureDeadlineForBothRoles(){
        for(Role role:new Role[]{Role.PROVIDER,Role.ADMIN}){
            for(LocalDateTime deadline:new LocalDateTime[]{null,now.plusSeconds(1)}){
                reset(repository,providerLocks,current,entityManager,audit,notification,email,waitlist,reports);
                appointment(now.plusSeconds(1));booking.setResponseDeadline(deadline);booking.setStatus(BookingStatus.PENDING);
                authorized(role);
                try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
                    time.when(LocalDateTime::now).thenReturn(now);
                    assertEquals(BookingStatus.CONFIRMED,service.updateBookingStatus(1L,BookingStatus.CONFIRMED).getStatus());
                }
                verify(repository).save(booking);
                var order=inOrder(providerLocks,entityManager,repository);
                order.verify(providerLocks).lockById(provider.getId());
                order.verify(entityManager).refresh(booking);
                order.verify(repository).save(booking);
            }
        }
    }

    @Test
    void elapsedOrExactDeadlineRejectsFutureAppointmentForBothRoles(){
        for(Role role:new Role[]{Role.PROVIDER,Role.ADMIN}){
            for(LocalDateTime deadline:new LocalDateTime[]{now.minusNanos(1),now}){
                reset(repository,providerLocks,current,entityManager,audit,notification,email,waitlist,reports);
                appointment(now.plusHours(1));booking.setResponseDeadline(deadline);booking.setStatus(BookingStatus.PENDING);
                authorized(role);
                try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
                    time.when(LocalDateTime::now).thenReturn(now);
                    var failure=assertThrows(InvalidBookingStatusException.class,
                            ()->service.updateBookingStatus(1L,BookingStatus.CONFIRMED));
                    assertEquals("Provider response deadline has passed",failure.getMessage());
                }
                unchanged();
            }
        }
    }

    @Test
    void clockIsReadAfterLockAndRefreshEvenWhenAppointmentWasInitiallyFuture(){
        appointment(now.plusSeconds(1));authorized(Role.PROVIDER);
        java.util.concurrent.atomic.AtomicBoolean refreshed=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation->{refreshed.set(true);return null;}).when(entityManager).refresh(booking);
        try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
            time.when(LocalDateTime::now).thenAnswer(invocation->refreshed.get()?now.plusSeconds(1):now);
            assertThrows(InvalidBookingStatusException.class,()->service.updateBookingStatus(1L,BookingStatus.CONFIRMED));
        }
        var order=inOrder(providerLocks,entityManager);
        order.verify(providerLocks).lockById(provider.getId());order.verify(entityManager).refresh(booking);
        unchanged();
    }

    @Test
    void refreshedAppointmentAndDeadlineAreUsedInsteadOfStaleValues(){
        authorized(Role.PROVIDER);
        doAnswer(invocation->{appointment(now);booking.setResponseDeadline(null);return null;})
                .when(entityManager).refresh(booking);
        try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
            time.when(LocalDateTime::now).thenReturn(now);
            assertThrows(InvalidBookingStatusException.class,()->service.updateBookingStatus(1L,BookingStatus.CONFIRMED));
        }
        unchanged();
    }

    @Test
    void refreshedResponseDeadlineIsEnforced(){
        booking.setResponseDeadline(now.plusHours(1));authorized(Role.PROVIDER);
        doAnswer(invocation->{booking.setResponseDeadline(now);return null;}).when(entityManager).refresh(booking);
        try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
            time.when(LocalDateTime::now).thenReturn(now);
            var failure=assertThrows(InvalidBookingStatusException.class,
                    ()->service.updateBookingStatus(1L,BookingStatus.CONFIRMED));
            assertEquals("Provider response deadline has passed",failure.getMessage());
        }
        unchanged();
    }

    @Test
    void existingDeadlinePolicyAlsoRemainsInForceForRejectionAndCancellation(){
        for(BookingStatus next:new BookingStatus[]{BookingStatus.REJECTED,BookingStatus.CANCELLED}){
            reset(repository,providerLocks,current,entityManager,audit,notification,email,waitlist,reports);
            booking.setResponseDeadline(now);authorized(Role.PROVIDER);
            try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
                time.when(LocalDateTime::now).thenReturn(now);
                assertThrows(InvalidBookingStatusException.class,()->service.updateBookingStatus(1L,next));
            }
            unchanged();
        }
    }

    @Test
    void reportBackedCompletionAfterAppointmentStartIsUnaffected(){
        appointment(now.minusHours(1));booking.setStatus(BookingStatus.CONFIRMED);authorized(Role.PROVIDER);
        when(reports.existsByBookingId(1L)).thenReturn(true);
        try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
            time.when(LocalDateTime::now).thenReturn(now);
            assertEquals(BookingStatus.COMPLETED,service.updateBookingStatus(1L,BookingStatus.COMPLETED).getStatus());
        }
        verify(repository).save(booking);
    }

    @Test
    void unrelatedProviderAndHomeownerAreDeniedBeforeLocking(){
        for(Role role:new Role[]{Role.PROVIDER,Role.HOMEOWNER}){
            reset(repository,current,providerLocks);
            User unrelated=new User();unrelated.setId(99L);unrelated.setRole(role);
            when(repository.findById(1L)).thenReturn(Optional.of(booking));
            when(current.getCurrentUser()).thenReturn(unrelated);
            assertThrows(UnauthorizedException.class,()->service.updateBookingStatus(1L,BookingStatus.CONFIRMED));
            verifyNoInteractions(providerLocks,entityManager,audit,notification,email,waitlist,reports);
            verify(repository,never()).save(any());
        }
    }

    @Test
    void terminalStatusRemainsUnchangedAndCannotBeConfirmed(){
        authorized(Role.PROVIDER);booking.setStatus(BookingStatus.CANCELLED);
        try(var time=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
            time.when(LocalDateTime::now).thenReturn(now);
            assertThrows(InvalidBookingStatusException.class,()->service.updateBookingStatus(1L,BookingStatus.CONFIRMED));
        }
        assertEquals(BookingStatus.CANCELLED,booking.getStatus());
        verify(repository,never()).save(any());verifyNoInteractions(audit,notification,email,waitlist,reports);
    }

    private void appointment(LocalDateTime start){booking.setBookingDate(start.toLocalDate());booking.setStartTime(start.toLocalTime());}
    private void authorized(Role role){
        User actor=providerUser;
        if(role==Role.ADMIN){actor=new User();actor.setId(40L);actor.setRole(Role.ADMIN);}
        when(repository.findById(1L)).thenReturn(Optional.of(booking));
        when(current.getCurrentUser()).thenReturn(actor);
        when(providerLocks.lockById(provider.getId())).thenReturn(Optional.of(provider));
    }
    private void unchanged(){
        assertEquals(BookingStatus.PENDING,booking.getStatus());
        verify(repository,never()).save(any());
        verifyNoInteractions(audit,notification,email,waitlist,reports);
    }
}
