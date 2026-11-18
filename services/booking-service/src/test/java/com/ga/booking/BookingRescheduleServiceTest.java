package com.ga.booking;

import com.ga.HomeHub.booking.client.AccountDirectory;
import com.ga.HomeHub.booking.config.BookingPolicyProperties;
import com.ga.HomeHub.booking.exception.BookingConflictException;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.Availability;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.BookingRescheduleRequest;
import com.ga.HomeHub.booking.model.BookingWaitlist;
import com.ga.HomeHub.booking.model.Category;
import com.ga.HomeHub.booking.model.ProviderProfile;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.ProviderStatus;
import com.ga.HomeHub.booking.model.enums.RescheduleStatus;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.model.enums.WaitlistStatus;
import com.ga.HomeHub.booking.repository.AvailabilityRepository;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.BookingRescheduleRequestRepository;
import com.ga.HomeHub.booking.repository.BookingWaitlistRepository;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import com.ga.HomeHub.booking.service.AuditLogService;
import com.ga.HomeHub.booking.service.BookingRescheduleService;
import com.ga.HomeHub.booking.service.BookingWaitlistService;
import com.ga.HomeHub.booking.service.CurrentUserService;
import com.ga.HomeHub.booking.service.NotificationService;
import com.ga.HomeHub.booking.validation.ProviderScheduleValidator;
import com.ga.HomeHub.booking.validation.ServiceEligibilityValidator;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

//Reschedule fixtures exercise participant reads and the existing locked approval path, not a second booking implementation.
@ExtendWith(MockitoExtension.class)
class BookingRescheduleServiceTest{
    @Mock private AccountDirectory fixtureAccounts;

    @Mock private BookingRescheduleRequestRepository requests;
    @Mock private BookingRepository bookings;
    @Mock private ProviderProfileRepository providers;
    @Mock private AvailabilityRepository availability;
    @Mock private CurrentUserService current;
    @Mock private AuditLogService audit;
    @Mock private BookingWaitlistRepository waiting;
    @Mock private BookingWaitlistService waitlist;
    @Mock private EntityManager entityManager;
    @Mock private NotificationService notifications;
    @InjectMocks private BookingRescheduleService service;
    private User homeowner;
    private User providerUser;
    private ProviderProfile provider;
    private Booking booking;
    private BookingRescheduleRequest request;

    @BeforeEach
    void setUp(){
        homeowner=new User();
        homeowner.setId(1L);
        providerUser=new User();
        providerUser.setId(2L);
        providerUser.setRole(Role.PROVIDER);
        provider=new ProviderProfile();
        provider.setId(3L);
        provider.setUser(providerUser);
        provider.setProviderStatus(ProviderStatus.APPROVED);
        ServiceOffering offering=new ServiceOffering();
        offering.setId(4L);
        offering.setCategory(new Category());
        offering.setName("Repair");
        offering.setDurationMinutes(60);
        offering.setProvider(provider);
        booking=new Booking();
        booking.setId(6L);
        booking.setCustomer(homeowner);
        booking.setService(offering);
        booking.setBookingDate(LocalDate.now().plusDays(3));
        booking.setStartTime(LocalTime.of(10,0));
        booking.setEndTime(LocalTime.of(11,0));
        booking.setStatus(BookingStatus.CONFIRMED);
        request=new BookingRescheduleRequest();
        request.setId(7L);
        request.setBooking(booking);
        request.setRequester(homeowner);
        request.setRequestedDate(LocalDate.now().plusDays(4));
        request.setRequestedStartTime(LocalTime.of(11,0));
    }

    @Test
    void participantListingScopesRoleFiltersAndPagination(){
        PageRequest page=PageRequest.of(1,2);
        when(current.getCurrentUser()).thenReturn(homeowner);
        when(requests.findCurrentUserRequests(1L,false,RescheduleStatus.PENDING,6L,page)).thenReturn(new PageImpl<>(List.of(request),page,3));
        assertEquals(3,service.getCurrentUserRequests(RescheduleStatus.PENDING,6L,page).getTotalElements());
        when(current.getCurrentUser()).thenReturn(providerUser);
        when(requests.findCurrentUserRequests(2L,true,null,null,page)).thenReturn(new PageImpl<>(List.of(request)));
        assertEquals(1,service.getCurrentUserRequests(null,null,page).getTotalElements());
        verify(requests).findCurrentUserRequests(1L,false,RescheduleStatus.PENDING,6L,page);
        verify(requests).findCurrentUserRequests(2L,true,null,null,page);
    }

    @Test
    void detailReadAllowsOnlyActualHomeownerOrAssignedProvider(){
        when(requests.findById(7L)).thenReturn(Optional.of(request));
        for(User user:List.of(homeowner,providerUser)){
            when(current.getCurrentUser()).thenReturn(user);
            assertSame(request,service.getRequestById(7L));
        }
        User other=new User();
        other.setId(99L);
        when(current.getCurrentUser()).thenReturn(other);
        assertThrows(UnauthorizedException.class,()->service.getRequestById(7L));
        other.setRole(Role.PROVIDER);
        assertThrows(UnauthorizedException.class,()->service.getRequestById(7L));
        other.setRole(Role.ADMIN);
        assertThrows(UnauthorizedException.class,()->service.getRequestById(7L));
    }

    @Test
    void adminCannotUseParticipantListing(){
        homeowner.setRole(Role.ADMIN);
        when(current.getCurrentUser()).thenReturn(homeowner);
        assertThrows(UnauthorizedException.class,()->service.getCurrentUserRequests(null,null,PageRequest.of(0,10)));
        verifyNoInteractions(requests);
    }

    @Test
    void requestLocksBeforeDuplicateCheckAndNotifiesWithoutChangingBooking(){
        ownBooking();
        when(requests.save(any())).thenAnswer(invocation->{
            BookingRescheduleRequest saved=invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        LocalDate originalDate=booking.getBookingDate();
        BookingRescheduleRequest result=service.request(6L,request.getRequestedDate(),request.getRequestedStartTime());
        assertEquals(RescheduleStatus.PENDING,result.getStatus());
        assertEquals(originalDate,booking.getBookingDate());
        var order=inOrder(providers,entityManager,requests,notifications);
        order.verify(providers).lockById(3L);
        order.verify(entityManager).refresh(booking);
        order.verify(requests).existsByBookingIdAndStatus(6L,RescheduleStatus.PENDING);
        order.verify(requests).save(any());
        order.verify(notifications).sendNotifications(eq(2L),contains("Reschedule request #7"));
        verify(bookings,never()).save(any());
    }

    @Test
    void duplicatePendingRequestIsRejectedAfterProviderLock(){
        ownBooking();
        when(requests.existsByBookingIdAndStatus(6L,RescheduleStatus.PENDING)).thenReturn(true);
        assertThrows(BookingConflictException.class,()->service.request(6L,request.getRequestedDate(),request.getRequestedStartTime()));
        verify(requests,never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void simultaneousRequestsHaveOneWinnerWithSimulatedProviderLock() throws Exception{
        ownBooking();
        ReentrantLock lock=new ReentrantLock();
        AtomicBoolean pending=new AtomicBoolean(false);
        when(providers.lockById(3L)).thenAnswer(invocation->{ lock.lock(); return Optional.of(provider); });
        when(requests.existsByBookingIdAndStatus(6L,RescheduleStatus.PENDING)).thenAnswer(invocation->pending.get());
        when(requests.save(any())).thenAnswer(invocation->{
            pending.set(true);
            BookingRescheduleRequest saved=invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> attempt=()->{
            start.await();
            try{
                service.request(6L,request.getRequestedDate(),request.getRequestedStartTime());
                return true;
            }catch(BookingConflictException e){
                return false;
            }finally{
                if(lock.isHeldByCurrentThread()){
                    lock.unlock();
                }
            }
        };
        try{
            Future<Boolean> first=executor.submit(attempt);
            Future<Boolean> second=executor.submit(attempt);
            start.countDown();
            assertNotEquals(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS));
            verify(requests,times(1)).save(any());
        }finally{
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void refreshedCancelledBookingCannotReceiveRequest(){
        ownBooking();
        doAnswer(invocation->{ booking.setStatus(BookingStatus.CANCELLED); return null; }).when(entityManager).refresh(booking);
        assertThrows(BookingConflictException.class,()->service.request(6L,request.getRequestedDate(),request.getRequestedStartTime()));
        verify(requests,never()).existsByBookingIdAndStatus(anyLong(),any());
        verify(requests,never()).save(any());
    }

    @Test
    void requestStillRequires24HoursNotice(){
        ownBooking();
        assertThrows(BookingConflictException.class,()->service.request(6L,LocalDate.now(),LocalTime.now().plusMinutes(1)));
        verifyNoInteractions(notifications);
        verify(requests,never()).save(any());
    }

    @Test
    void unrelatedHomeownerCannotRequestBeforeAnyLock(){
        when(bookings.findById(6L)).thenReturn(Optional.of(booking));
        User other=new User();
        other.setId(99L);
        when(current.getCurrentUser()).thenReturn(other);
        assertThrows(UnauthorizedException.class,()->service.request(6L,request.getRequestedDate(),request.getRequestedStartTime()));
        verifyNoInteractions(providers,requests,notifications);
    }

    @Test
    void approvalRechecksUnderLockResetsRemindersAndReleasesOldDate(){
        assignedDecision();
        availableSlot();
        booking.setReminder24Sent(true);
        booking.setReminder1Sent(true);
        LocalDate oldDate=booking.getBookingDate();
        when(requests.save(request)).thenReturn(request);
        assertSame(request,service.decide(7L,true));
        assertEquals(RescheduleStatus.APPROVED,request.getStatus());
        assertEquals(request.getRequestedDate(),booking.getBookingDate());
        assertEquals(LocalTime.of(12,0),booking.getEndTime());
        assertFalse(booking.isReminder24Sent());
        assertFalse(booking.isReminder1Sent());
        verify(waitlist).offerNextAvailableProviderSlot(3L,oldDate);
        verify(notifications).sendNotifications(1L,"Reschedule request #7 for booking #6 was approved");
        verify(requests,times(1)).save(request);
        var order=inOrder(providers,entityManager,availability,bookings);
        order.verify(providers).lockById(3L);
        order.verify(entityManager).refresh(request);
        order.verify(entityManager).refresh(booking);
        order.verify(availability).findByProviderIdAndDateAndIsAvailableTrue(3L,request.getRequestedDate());
        order.verify(bookings).save(booking);
    }

    @Test
    void rejectionNotifiesWithoutChangingAppointmentOrReminders(){
        assignedDecision();
        when(requests.save(request)).thenReturn(request);
        LocalDate original=booking.getBookingDate();
        service.decide(7L,false);
        assertEquals(RescheduleStatus.REJECTED,request.getStatus());
        assertEquals(original,booking.getBookingDate());
        verify(bookings,never()).save(any());
        verifyNoInteractions(availability,waiting,waitlist);
        verify(notifications).sendNotifications(1L,"Reschedule request #7 for booking #6 was rejected");
    }

    @Test
    void decisionRejectsConcurrentDecisionSeenAfterRefresh(){
        assignedDecision();
        doAnswer(invocation->{ request.setStatus(RescheduleStatus.APPROVED); return null; }).when(entityManager).refresh(request);
        assertThrows(BookingConflictException.class,()->service.decide(7L,true));
        verify(requests,never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void approvalRechecks24HourNoticeAtDecisionTime(){
        assignedDecision();
        request.setRequestedDate(LocalDate.now());
        assertThrows(BookingConflictException.class,()->service.decide(7L,true));
        verifyNoInteractions(availability,notifications);
    }

    @Test
    void approvalRejectsProviderTimeOffWithoutChangingOriginalBooking(){
        assignedDecision();
        availableSlot();
        Availability block=new Availability();
        block.setStartTime(LocalTime.of(11,0));
        block.setEndTime(LocalTime.of(12,0));
        when(availability.findByProviderIdAndDateAndTimeOffTrue(3L,request.getRequestedDate())).thenReturn(List.of(block));
        LocalDate original=booking.getBookingDate();
        assertThrows(BookingConflictException.class,()->service.decide(7L,true));
        assertEquals(original,booking.getBookingDate());
        verify(bookings,never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void approvalRejectsOverlappingBookingAndProviderWideOffer(){
        assignedDecision();
        availableSlot();
        Booking other=new Booking();
        other.setId(99L);
        other.setStartTime(LocalTime.of(11,0));
        other.setEndTime(LocalTime.of(12,0));
        when(bookings.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList())).thenReturn(List.of(other));
        assertThrows(BookingConflictException.class,()->service.decide(7L,true));
        when(bookings.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList())).thenReturn(List.of());
        BookingWaitlist offer=new BookingWaitlist();
        offer.setRequestedStartTime(LocalTime.of(10,30));
        offer.setService(booking.getService());
        offer.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));
        when(waiting.findByServiceProviderIdAndRequestedDateAndStatus(3L,request.getRequestedDate(),WaitlistStatus.OFFERED)).thenReturn(List.of(offer));
        assertThrows(BookingConflictException.class,()->service.decide(7L,true));
        verify(bookings,never()).save(any());
        verifyNoInteractions(notifications);
    }

    private void ownBooking(){
        when(bookings.findById(6L)).thenReturn(Optional.of(booking));
        when(current.getCurrentUser()).thenReturn(homeowner);
        when(providers.lockById(3L)).thenReturn(Optional.of(provider));
    }

    private void assignedDecision(){
        when(requests.findById(7L)).thenReturn(Optional.of(request));
        when(current.getCurrentUser()).thenReturn(providerUser);
        when(providers.lockById(3L)).thenReturn(Optional.of(provider));
    }

    private void availableSlot(){
        Availability window=new Availability();
        window.setStartTime(LocalTime.of(9,0));
        window.setEndTime(LocalTime.of(17,0));
        when(availability.findByProviderIdAndDateAndIsAvailableTrue(3L,request.getRequestedDate())).thenReturn(List.of(window));
    }

    @BeforeEach
    void wireCurrentProductionDependencies(){
        Mockito.lenient().when(fixtureAccounts.isActive(ArgumentMatchers.any())).thenReturn(true);
        ReflectionTestUtils.setField(service,"schedule",new ProviderScheduleValidator(availability,bookings,waiting));
        ReflectionTestUtils.setField(service,"policy",new BookingPolicyProperties());
        ReflectionTestUtils.setField(service,"eligibility",new ServiceEligibilityValidator(entityManager,fixtureAccounts));
    }
}
