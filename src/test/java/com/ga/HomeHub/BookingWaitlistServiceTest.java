package com.ga.HomeHub;

import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.*;
import com.ga.HomeHub.model.enums.*;
import com.ga.HomeHub.repository.*;
import com.ga.HomeHub.service.BookingWaitlistService;
import com.ga.HomeHub.service.CurrentUserService;
import com.ga.HomeHub.service.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

//Run the existing waitlist service to protect FIFO, expiration and claim behavior; production logic is unchanged.
@ExtendWith(MockitoExtension.class)
class BookingWaitlistServiceTest{
    @Mock private BookingWaitlistRepository waiting;
    @Mock private ServiceOfferingRepository services;
    @Mock private HomeRepository homes;
    @Mock private BookingRepository bookings;
    @Mock private ProviderProfileRepository providers;
    @Mock private AvailabilityRepository availability;
    @Mock private CurrentUserService current;
    @Mock private NotificationService notifications;
    @Mock private EntityManager entityManager;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private BookingWaitlistService service;
    private User homeowner;
    private ProviderProfile provider;
    private ServiceOffering offering;
    private BookingWaitlist entry;

    @BeforeEach
    void setUp(){
        homeowner=new User();
        homeowner.setId(1L);
        User providerUser=new User();
        providerUser.setId(2L);
        provider=new ProviderProfile();
        provider.setId(3L);
        provider.setUser(providerUser);
        provider.setProviderStatus(ProviderStatus.APPROVED);
        offering=new ServiceOffering();
        offering.setId(4L);
        offering.setProvider(provider);
        offering.setDurationMinutes(60);
        offering.setPrice(50.0);
        Home home=new Home();
        home.setId(5L);
        home.setOwner(homeowner);
        entry=new BookingWaitlist();
        entry.setId(8L);
        entry.setHomeowner(homeowner);
        entry.setHome(home);
        entry.setService(offering);
        entry.setRequestedDate(LocalDate.now().plusDays(3));
        entry.setRequestedStartTime(LocalTime.of(10,0));
    }

    @Test
    void providerWideFifoCreatesOneOverlappingOfferWith15MinuteDeadline(){
        when(providers.lockById(3L)).thenReturn(Optional.of(provider));
        BookingWaitlist second=new BookingWaitlist();
        second.setId(9L);
        second.setHomeowner(homeowner);
        second.setHome(entry.getHome());
        ServiceOffering otherService=new ServiceOffering();
        otherService.setId(10L);
        otherService.setProvider(provider);
        otherService.setDurationMinutes(90);
        second.setService(otherService);
        second.setRequestedDate(entry.getRequestedDate());
        second.setRequestedStartTime(entry.getRequestedStartTime());
        List<BookingWaitlist> queue=List.of(entry,second);
        when(waiting.findByServiceProviderIdAndRequestedDateAndStatusOrderByCreatedAtAscIdAsc(3L,entry.getRequestedDate(),WaitlistStatus.WAITING))
                .thenReturn(queue);
        when(waiting.findByServiceProviderIdAndRequestedDateAndStatus(3L,entry.getRequestedDate(),WaitlistStatus.OFFERED))
                .thenAnswer(invocation->queue.stream().filter(w->w.getStatus()==WaitlistStatus.OFFERED).toList());
        availableWindow();
        LocalDateTime before=LocalDateTime.now();
        service.offerNextAvailableProviderSlot(3L,entry.getRequestedDate());
        LocalDateTime after=LocalDateTime.now();
        assertEquals(WaitlistStatus.OFFERED,entry.getStatus());
        assertEquals(WaitlistStatus.WAITING,second.getStatus());
        assertFalse(entry.getOfferExpiresAt().isBefore(before.plusMinutes(15)));
        assertFalse(entry.getOfferExpiresAt().isAfter(after.plusMinutes(15)));
        verify(waiting).save(entry);
        verify(waiting,never()).save(second);
        verify(notifications).sendNotifications(eq(1L),contains("expires in 15 minutes"));
    }

    @Test
    void expiredOfferCannotBeClaimedAndRefreshHappensAfterLock(){
        ownOffer();
        entry.setOfferExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(BookingConflictException.class,()->service.claimWaitlistOffer(8L));
        var order=inOrder(providers,entityManager);
        order.verify(providers).lockById(3L);
        order.verify(entityManager).refresh(entry);
        verify(bookings,never()).save(any());
        verify(waiting,never()).save(any());
    }

    @Test
    void claimRechecksStateChangedWhileWaitingForLock(){
        ownOffer();
        doAnswer(invocation->{ entry.setStatus(WaitlistStatus.CLAIMED); return null; }).when(entityManager).refresh(entry);
        assertThrows(BookingConflictException.class,()->service.claimWaitlistOffer(8L));
        verify(bookings,never()).save(any());
    }

    @Test
    void successfulClaimCreatesNormalBookingAndMarksEntryClaimed(){
        ownOffer();
        availableWindow();
        when(bookings.save(any())).thenAnswer(invocation->{
            Booking booking=invocation.getArgument(0);
            booking.setId(11L);
            return booking;
        });
        Booking booking=service.claimWaitlistOffer(8L);
        assertEquals(WaitlistStatus.CLAIMED,entry.getStatus());
        assertEquals(BookingType.NORMAL,booking.getBookingType());
        assertEquals(BookingStatus.PENDING,booking.getStatus());
        assertEquals(50.0,booking.getFinalPrice());
        assertEquals(LocalTime.of(11,0),booking.getEndTime());
        assertNotNull(booking.getResponseDeadline());
        verify(notifications).sendNotifications(eq(2L),contains("booking #11"));
    }

    @Test
    void unrelatedHomeownerCannotClaimOffer(){
        when(waiting.findById(8L)).thenReturn(Optional.of(entry));
        User other=new User();
        other.setId(99L);
        when(current.getCurrentUser()).thenReturn(other);
        assertThrows(UnauthorizedException.class,()->service.claimWaitlistOffer(8L));
        verifyNoInteractions(providers,entityManager);
    }

    @Test
    void eachExpirationCandidateUsesSeparateRequiresNewTransaction(){
        BookingWaitlist other=new BookingWaitlist();
        other.setId(9L);
        when(waiting.findByStatusAndOfferExpiresAtBefore(eq(WaitlistStatus.OFFERED),any())).thenReturn(List.of(entry,other));
        when(waiting.findById(anyLong())).thenReturn(Optional.empty());
        List<Integer> propagation=new ArrayList<>();
        when(transactionManager.getTransaction(any())).thenAnswer(invocation->{
            TransactionDefinition definition=invocation.getArgument(0);
            propagation.add(definition.getPropagationBehavior());
            return new SimpleTransactionStatus();
        });
        service.expireWaitlistOffers();
        assertEquals(List.of(TransactionDefinition.PROPAGATION_REQUIRES_NEW,TransactionDefinition.PROPAGATION_REQUIRES_NEW),propagation);
        verify(transactionManager,times(2)).commit(any());
        verify(waiting).findById(8L);
        verify(waiting).findById(9L);
    }

    private void ownOffer(){
        when(waiting.findById(8L)).thenReturn(Optional.of(entry));
        when(current.getCurrentUser()).thenReturn(homeowner);
        when(providers.lockById(3L)).thenReturn(Optional.of(provider));
        entry.setStatus(WaitlistStatus.OFFERED);
        entry.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));
    }

    private void availableWindow(){
        Availability window=new Availability();
        window.setStartTime(LocalTime.of(9,0));
        window.setEndTime(LocalTime.of(17,0));
        when(availability.findByProviderIdAndDateAndIsAvailableTrue(3L,entry.getRequestedDate())).thenReturn(List.of(window));
    }
}
