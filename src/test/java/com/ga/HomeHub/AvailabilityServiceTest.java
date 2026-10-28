package com.ga.HomeHub;

import com.ga.HomeHub.dto.AvailabilityRequest;
import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Availability;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.repository.AvailabilityRepository;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import com.ga.HomeHub.service.AvailabilityService;
import com.ga.HomeHub.service.BookingWaitlistService;
import com.ga.HomeHub.service.ProviderService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

//Period management needs separate fixtures from booking creation; no development database is touched.
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest{
    @Mock private AvailabilityRepository repository;
    @Mock private ProviderService providers;
    @Mock private ProviderProfileRepository providerLocks;
    @Mock private BookingRepository bookings;
    @Mock private EntityManager entityManager;
    @Mock private BookingWaitlistService waitlist;
    @InjectMocks private AvailabilityService service;
    private ProviderProfile provider;
    private Availability period;
    private final LocalDate date=LocalDate.now().plusDays(3);

    @BeforeEach
    void setUp(){
        provider=new ProviderProfile();
        provider.setId(11L);
        period=new Availability();
        period.setId(5L);
        period.setProvider(provider);
        period.setDate(date);
        period.setStartTime(LocalTime.of(9,0));
        period.setEndTime(LocalTime.of(17,0));
    }

    @Test
    void listingPublishedWindowsKeepsExistingRepositoryRead(){
        when(repository.findByProviderIdAndDateAndIsAvailableTrue(11L,date)).thenReturn(List.of(period));
        assertEquals(List.of(period),service.getProviderAvailabilityByDate(11L,date,false));
        verifyNoInteractions(providers);
    }

    @Test
    void blockedPeriodListingUsesExistingQueryAndRequiresOwnership(){
        when(providers.getCurrentProviderProfile()).thenReturn(provider);
        when(repository.findByProviderIdAndDateAndTimeOffTrue(11L,date)).thenReturn(List.of(period));
        assertEquals(List.of(period),service.getProviderAvailabilityByDate(11L,date,true));
        assertThrows(UnauthorizedException.class,()->service.getProviderAvailabilityByDate(99L,date,true));
        verify(repository,never()).findByProviderIdAndDateAndTimeOffTrue(99L,date);
    }

    @Test
    void removingTimeOffClearsBlockingFlagAfterLockAndRefresh(){
        ownPeriod();
        period.setAvailable(false);
        period.setTimeOff(true);
        service.deleteAvailability(5L);
        assertFalse(period.isAvailable());
        assertFalse(period.isTimeOff());
        InOrder order=inOrder(providerLocks,entityManager,bookings,repository,waitlist);
        order.verify(providerLocks).lockById(11L);
        order.verify(entityManager).refresh(period);
        order.verify(bookings).findByServiceProviderIdAndBookingDateAndStatusIn(eq(11L),eq(date),anyList());
        order.verify(repository).save(period);
        order.verify(waitlist).offerNextAvailableProviderSlot(11L,date);
    }

    @Test
    void removingOrdinaryAvailabilityRemainsSoftDeletion(){
        ownPeriod();
        service.deleteAvailability(5L);
        assertFalse(period.isAvailable());
        verify(repository).save(period);
        verify(repository,never()).delete(any());
        verifyNoInteractions(waitlist);
    }

    @Test
    void removingTimeOffDoesNotEnableOrChangeOtherPeriods(){
        ownPeriod();
        period.setAvailable(false);
        period.setTimeOff(true);
        Availability otherPublished=new Availability();
        otherPublished.setId(6L);
        Availability otherUnavailable=new Availability();
        otherUnavailable.setId(7L);
        otherUnavailable.setAvailable(false);
        service.deleteAvailability(5L);
        assertFalse(period.isAvailable());
        assertFalse(period.isTimeOff());
        assertEquals(date,period.getDate());
        assertEquals(LocalTime.of(9,0),period.getStartTime());
        assertEquals(LocalTime.of(17,0),period.getEndTime());
        assertTrue(otherPublished.isAvailable());
        assertFalse(otherUnavailable.isAvailable());
        verify(repository).save(period);
        verify(repository,never()).save(otherPublished);
        verify(repository,never()).save(otherUnavailable);
        verify(repository,never()).saveAll(any());
    }

    @Test
    void removalUsesPeriodDateAndRangeRefreshedAfterConcurrentEdit(){
        ownPeriod();
        period.setAvailable(false);
        period.setTimeOff(true);
        LocalDate changedDate=date.plusDays(1);
        doAnswer(invocation->{
            period.setDate(changedDate);
            period.setStartTime(LocalTime.of(12,0));
            period.setEndTime(LocalTime.of(13,0));
            return null;
        }).when(entityManager).refresh(period);
        service.deleteAvailability(5L);
        verify(bookings).findByServiceProviderIdAndBookingDateAndStatusIn(eq(11L),eq(changedDate),anyList());
        verify(bookings,never()).findByServiceProviderIdAndBookingDateAndStatusIn(eq(11L),eq(date),anyList());
        verify(waitlist).offerNextAvailableProviderSlot(11L,changedDate);
        assertFalse(period.isAvailable());
        assertFalse(period.isTimeOff());
    }

    @Test
    void removalRechecksBookingCommittedWhileWaitingForProviderLock(){
        ownPeriod();
        period.setAvailable(false);
        period.setTimeOff(true);
        doAnswer(invocation->{
            when(bookings.findByServiceProviderIdAndBookingDateAndStatusIn(eq(11L),eq(date),anyList()))
                    .thenReturn(List.of(booking(10,11)));
            return Optional.of(provider);
        }).when(providerLocks).lockById(11L);
        assertThrows(BookingConflictException.class,()->service.deleteAvailability(5L));
        assertTrue(period.isTimeOff());
        assertFalse(period.isAvailable());
        verify(repository,never()).save(any());
        verifyNoInteractions(waitlist);
    }

    @Test
    void repeatedRemovalAfterConcurrentDeletionDoesNotReleaseWaitlistAgain(){
        ownPeriod();
        period.setAvailable(false);
        period.setTimeOff(true);
        doAnswer(invocation->{ period.setTimeOff(false); return null; }).when(entityManager).refresh(period);
        service.deleteAvailability(5L);
        assertFalse(period.isTimeOff());
        assertFalse(period.isAvailable());
        verifyNoInteractions(waitlist);
    }

    @Test
    void editsAndRemovalRejectOwnershipChangeFoundAfterLock(){
        ownPeriod();
        ProviderProfile other=new ProviderProfile();
        other.setId(99L);
        doAnswer(invocation->{ period.setProvider(other); return null; }).when(entityManager).refresh(period);
        assertThrows(UnauthorizedException.class,()->service.deleteAvailability(5L));
        period.setProvider(provider);
        assertThrows(UnauthorizedException.class,()->service.updateAvailability(5L,request(10,12)));
        verify(entityManager,times(2)).refresh(period);
        verifyNoInteractions(bookings,waitlist);
        verify(repository,never()).save(any());
    }

    @Test
    void mutationStopsWhenProviderLockCannotBeAcquired(){
        ownPeriod();
        when(providerLocks.lockById(11L)).thenReturn(Optional.empty());
        assertThrows(InformationNotFoundException.class,()->service.deleteAvailability(5L));
        assertThrows(InformationNotFoundException.class,()->service.updateAvailability(5L,request(10,12)));
        verifyNoInteractions(entityManager,bookings,waitlist);
        verify(repository,never()).save(any());
    }

    @Test
    void removalStillRejectsActiveBookingOverlap(){
        ownPeriod();
        when(bookings.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList())).thenReturn(List.of(booking(10,11)));
        assertThrows(BookingConflictException.class,()->service.deleteAvailability(5L));
        assertTrue(period.isAvailable());
        verify(repository,never()).save(any());
    }

    @Test
    void anotherProviderCannotEditOrRemovePeriod(){
        when(repository.findById(5L)).thenReturn(Optional.of(period));
        ProviderProfile other=new ProviderProfile();
        other.setId(99L);
        when(providers.getCurrentProviderProfile()).thenReturn(other);
        assertThrows(UnauthorizedException.class,()->service.deleteAvailability(5L));
        assertThrows(UnauthorizedException.class,()->service.updateAvailability(5L,request(10,12)));
        verifyNoInteractions(providerLocks,entityManager);
        verify(repository,never()).save(any());
    }

    @Test
    void editingAvailablePeriodReusesEntityAndLocksBeforeConflictCheck(){
        ownPeriod();
        when(repository.save(period)).thenReturn(period);
        assertSame(period,service.updateAvailability(5L,request(10,12)));
        assertEquals(LocalTime.of(10,0),period.getStartTime());
        assertEquals(LocalTime.of(12,0),period.getEndTime());
        assertTrue(period.isAvailable());
        InOrder order=inOrder(providerLocks,entityManager,bookings,repository);
        order.verify(providerLocks).lockById(11L);
        order.verify(entityManager).refresh(period);
        order.verify(bookings).findByServiceProviderIdAndBookingDateAndStatusIn(eq(11L),eq(date),anyList());
        order.verify(repository).save(period);
    }

    @Test
    void editRejectsPreviouslyDeletedPeriodAfterRefresh(){
        ownPeriod();
        doAnswer(invocation->{ period.setAvailable(false); return null; }).when(entityManager).refresh(period);
        assertThrows(IllegalArgumentException.class,()->service.updateAvailability(5L,request(10,12)));
        verifyNoInteractions(bookings);
        verify(repository,never()).save(any());
    }

    @Test
    void editRejectsOverlapWithOriginalBookedWindow(){
        ownPeriod();
        when(bookings.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList())).thenReturn(List.of(booking(9,10)));
        assertThrows(BookingConflictException.class,()->service.updateAvailability(5L,request(11,12)));
        assertEquals(LocalTime.of(9,0),period.getStartTime());
        verify(repository,never()).save(any());
    }

    @Test
    void movingTimeOffCannotBlockBookingAtNewDate(){
        ownPeriod();
        period.setTimeOff(true);
        period.setAvailable(false);
        LocalDate nextDate=date.plusDays(1);
        when(bookings.findByServiceProviderIdAndBookingDateAndStatusIn(eq(11L),any(),anyList()))
                .thenAnswer(invocation->nextDate.equals(invocation.getArgument(1))?List.of(booking(10,11)):List.of());
        assertThrows(BookingConflictException.class,()->service.updateAvailability(5L,
                new AvailabilityRequest(nextDate,LocalTime.of(10,0),LocalTime.of(12,0))));
        assertEquals(date,period.getDate());
        verify(repository,never()).save(any());
    }

    @Test
    void movingTimeOffRechecksWaitlistOnReleasedDate(){
        ownPeriod();
        period.setTimeOff(true);
        period.setAvailable(false);
        when(repository.save(period)).thenReturn(period);
        service.updateAvailability(5L,new AvailabilityRequest(date.plusDays(1),LocalTime.of(10,0),LocalTime.of(12,0)));
        assertTrue(period.isTimeOff());
        assertFalse(period.isAvailable());
        verify(waitlist).offerNextAvailableProviderSlot(11L,date);
    }

    @Test
    void createAndTimeOffRetainExistingRangeValidation(){
        for(AvailabilityRequest invalid:List.of(request(12,10),request(10,10),
                new AvailabilityRequest(LocalDate.now().minusDays(1),LocalTime.of(9,0),LocalTime.of(10,0)))){
            assertThrows(IllegalArgumentException.class,()->service.createAvailability(invalid));
            assertThrows(IllegalArgumentException.class,()->service.blockTimeOff(invalid));
            assertThrows(IllegalArgumentException.class,()->service.updateAvailability(5L,invalid));
        }
        verifyNoInteractions(repository,providerLocks);
    }

    @Test
    void blockingTimeOffRetainsLockAndConflictValidation(){
        when(providers.getCurrentProviderProfile()).thenReturn(provider);
        when(bookings.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList())).thenReturn(List.of(booking(10,11)));
        assertThrows(BookingConflictException.class,()->service.blockTimeOff(request(9,12)));
        InOrder order=inOrder(providerLocks,bookings);
        order.verify(providerLocks).lockById(11L);
        order.verify(bookings).findByServiceProviderIdAndBookingDateAndStatusIn(eq(11L),eq(date),anyList());
        verify(repository,never()).save(any());
    }

    private void ownPeriod(){
        when(repository.findById(5L)).thenReturn(Optional.of(period));
        when(providers.getCurrentProviderProfile()).thenReturn(provider);
        lenient().when(providerLocks.lockById(11L)).thenReturn(Optional.of(provider));
    }

    private AvailabilityRequest request(int start,int end){
        return new AvailabilityRequest(date,LocalTime.of(start,0),LocalTime.of(end,0));
    }

    private Booking booking(int start,int end){
        Booking booking=new Booking();
        booking.setStartTime(LocalTime.of(start,0));
        booking.setEndTime(LocalTime.of(end,0));
        return booking;
    }
}
