
package com.ga.HomeHub;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.dto.auth.LoginRequest;
import com.ga.HomeHub.dto.auth.RegisterRequest;
import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationExistException;
import com.ga.HomeHub.exception.InvalidBookingStatusException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Availability;
import com.ga.HomeHub.model.AuditLog;
import com.ga.HomeHub.model.Category;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.BookingWaitlist;
import com.ga.HomeHub.model.Home;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.model.enums.BookingType;
import com.ga.HomeHub.model.enums.CategoryStatus;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.ServiceStatus;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.model.enums.WaitlistStatus;
import com.ga.HomeHub.repository.AvailabilityRepository;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.BookingWaitlistRepository;
import com.ga.HomeHub.repository.HomeRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import com.ga.HomeHub.repository.ServiceOfferingRepository;
import com.ga.HomeHub.repository.ServiceReportRepository;
import com.ga.HomeHub.repository.UserRepository;
import com.ga.HomeHub.service.AuditLogService;
import com.ga.HomeHub.service.AuthService;
import com.ga.HomeHub.service.BookingService;
import com.ga.HomeHub.service.BookingWaitlistService;
import com.ga.HomeHub.service.CurrentUserService;
import com.ga.HomeHub.service.EmailService;
import com.ga.HomeHub.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import static org.mockito.Mockito.doAnswer;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HomeHubServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private HomeRepository homeRepository;

    @Mock
    private ServiceOfferingRepository serviceRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ProviderProfileRepository providerLocks;

    @Mock
    private BookingWaitlistRepository waiting;

    @Mock
    private BookingWaitlistService waitlist;

    @Mock
    private ServiceReportRepository reports;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AuditLogService audit;

    @Mock
    private NotificationService notification;

    @Mock
    private EmailService email;

    @InjectMocks
    private AuthService authService;

    @InjectMocks
    private BookingService bookingService;

    @Test
    @DisplayName("Login fails when email is not verified")
    void loginFailsWhenEmailIsNotVerified(){
        User user=new User();
        user.setEmailAddress("user@gmail.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        when(userRepository.findUserByEmailAddress("user@gmail.com")).thenReturn(user);

        LoginRequest request=new LoginRequest("user@gmail.com","Password123");

        assertThrows(UnauthorizedException.class,()->authService.loginUser(request));
    }

    @Test
    @DisplayName("Inactive user cannot login")
    void inactiveUserCannotLogin(){
        User user=new User();
        user.setEmailAddress("user@gmail.com");
        user.setStatus(UserStatus.INACTIVE);
        user.setEmailVerified(true);

        when(userRepository.findUserByEmailAddress("user@gmail.com")).thenReturn(user);

        LoginRequest request=new LoginRequest("user@gmail.com","Password123");

        assertThrows(UnauthorizedException.class,()->authService.loginUser(request));
    }

    @Test
    @DisplayName("Registration rejects an invalid email")
    void registerRequestRejectsInvalidEmail(){
        RegisterRequest request=new RegisterRequest("Sara","Ahmed","not-an-email","Password123","33333333",Role.HOMEOWNER);

        try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
            Validator validator=factory.getValidator();
            Set<ConstraintViolation<RegisterRequest>> errors=validator.validate(request);

            assertFalse(errors.isEmpty());
        }
    }

    @Test
    @DisplayName("Registration rejects a password shorter than 8 characters")
    void registerRequestRejectsShortPassword(){
        RegisterRequest request=new RegisterRequest("Sara","Ahmed","sara@gmail.com","123","33333333",Role.HOMEOWNER);

        try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
            Validator validator=factory.getValidator();
            Set<ConstraintViolation<RegisterRequest>> errors=validator.validate(request);

            assertFalse(errors.isEmpty());
        }
    }

    @Test
    @DisplayName("Booking fails when requested time is outside provider availability")
    void bookingFailsWhenTimeIsOutsideProviderAvailability(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        Home home=createHome(1L,homeowner);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);

        BookingRequest request=new BookingRequest(1L,1L,date,LocalTime.of(10,0),"AC repair",BookingType.NORMAL);

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList()))
                .thenReturn(Collections.emptyList());

        assertThrows(BookingConflictException.class,()->bookingService.createBooking(request));
    }

    @Test
    @DisplayName("Booking fails when provider is already booked")
    void bookingFailsWhenProviderIsAlreadyBooked(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        Home home=createHome(1L,homeowner);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);

        Availability available=createAvailability(provider,date);

        Booking existingBooking=new Booking();
        existingBooking.setStartTime(LocalTime.of(10,0));
        existingBooking.setEndTime(LocalTime.of(11,0));
        existingBooking.setStatus(BookingStatus.CONFIRMED);

        BookingRequest request=new BookingRequest(1L,1L,date,LocalTime.of(10,30),"AC repair",BookingType.NORMAL);

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date))
                .thenReturn(List.of(available));
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList()))
                .thenReturn(List.of(existingBooking));

        assertThrows(BookingConflictException.class,()->bookingService.createBooking(request));
    }

    @Test
    @DisplayName("Completed booking cannot be cancelled")
    void completedBookingCannotBeCancelled(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        assertThrows(InvalidBookingStatusException.class,()->bookingService.cancelBooking(1L));
    }

    @Test
    @DisplayName("Homeowner can cancel their own booking")
    void homeownerCanCancelOwnBooking(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        homeowner.setEmailAddress("homeowner@gmail.com");

        User providerUser=createUser(2L,Role.PROVIDER);
        providerUser.setEmailAddress("provider@gmail.com");

        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setBookingDate(LocalDate.now().plusDays(3));
        booking.setStartTime(LocalTime.of(10,0));
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        Booking result=bookingService.cancelBooking(1L);

        assertEquals(BookingStatus.CANCELLED,result.getStatus());
    }

    @Test
    @DisplayName("Homeowner cannot update booking status")
    void homeownerCannotUpdateBookingStatus(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);

        assertThrows(UnauthorizedException.class,()->bookingService.updateBookingStatus(1L,BookingStatus.CONFIRMED));
    }

    @Test
    @DisplayName("User cannot cancel another user's booking")
    void userCannotCancelAnotherUsersBooking(){
        User owner=createUser(1L,Role.HOMEOWNER);
        User otherUser=createUser(2L,Role.HOMEOWNER);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(owner);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(otherUser);

        assertThrows(UnauthorizedException.class,()->bookingService.cancelBooking(1L));
    }

    @Test
    @DisplayName("Booking cannot be created for a past date")
    void bookingCannotBeCreatedInThePast(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        Home home=createHome(1L,homeowner);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate pastDate=LocalDate.now().minusDays(1);

        BookingRequest request=new BookingRequest(1L,1L,pastDate,LocalTime.of(10,0),"AC repair",BookingType.NORMAL);

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        assertThrows(BookingConflictException.class,()->bookingService.createBooking(request));
    }

    @Test
    @DisplayName("User cannot create a booking for another user's home")
    void userCannotBookAnotherUsersHome(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User otherOwner=createUser(2L,Role.HOMEOWNER);
        Home home=createHome(1L,otherOwner);
        User providerUser=createUser(3L,Role.PROVIDER);
        ProviderProfile provider=createProvider(3L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);

        BookingRequest request=new BookingRequest(1L,1L,date,LocalTime.of(10,0),"Cleaning",BookingType.NORMAL);

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(3L)).thenReturn(Optional.of(provider));

        assertThrows(UnauthorizedException.class,()->bookingService.createBooking(request));
    }

    @Test
    @DisplayName("Inactive service cannot be booked")
    void inactiveServiceCannotBeBooked(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        Home home=createHome(1L,homeowner);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        service.setStatus(ServiceStatus.INACTIVE);
        LocalDate date=LocalDate.now().plusDays(3);

        BookingRequest request=new BookingRequest(1L,1L,date,LocalTime.of(10,0),"Cleaning",BookingType.NORMAL);

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList()))
                .thenReturn(Collections.emptyList());

        assertThrows(BookingConflictException.class,()->bookingService.createBooking(request));
    }

    @Test
    @DisplayName("Pending booking can be confirmed")
    void pendingBookingCanBeConfirmed(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        homeowner.setEmailAddress("homeowner@gmail.com");

        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingDate(LocalDate.now().plusDays(2));
        booking.setStartTime(LocalTime.of(10,0));

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(providerUser);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        Booking result=bookingService.updateBookingStatus(1L,BookingStatus.CONFIRMED);

        assertEquals(BookingStatus.CONFIRMED,result.getStatus());
    }

    @Test
    @DisplayName("Duplicate email cannot be registered")
    void duplicateEmailCannotRegister(){
        RegisterRequest request=new RegisterRequest("Sara","Ahmed","sara@gmail.com","Password123","33333333",Role.HOMEOWNER);

        when(userRepository.existsByEmailAddress("sara@gmail.com")).thenReturn(true);

        assertThrows(InformationExistException.class,()->authService.registerUser(request));
    }

    @Test
    @DisplayName("Admin cannot self-register")
    void adminCannotSelfRegister(){
        RegisterRequest request=new RegisterRequest("Admin","User","admin@gmail.com","Password123","33333333",Role.ADMIN);

        when(userRepository.existsByEmailAddress("admin@gmail.com")).thenReturn(false);

        assertThrows(UnauthorizedException.class,()->authService.registerUser(request));
    }

    @Test
    @DisplayName("Cancelled booking cannot be confirmed")
    void cancelledBookingCannotBeConfirmed(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(providerUser);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        assertThrows(InvalidBookingStatusException.class,()->bookingService.updateBookingStatus(1L,BookingStatus.CONFIRMED));
    }

    @Test
    @DisplayName("Confirmed booking can be completed when report exists")
    void confirmedBookingCanBeCompleted(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        homeowner.setEmailAddress("homeowner@gmail.com");

        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(providerUser);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(reports.existsByBookingId(1L)).thenReturn(true);

        Booking result=bookingService.updateBookingStatus(1L,BookingStatus.COMPLETED);

        assertEquals(BookingStatus.COMPLETED,result.getStatus());
    }

    @Test
    @DisplayName("Cancelling an already cancelled booking keeps it cancelled")
    void cancellingAlreadyCancelledBookingKeepsItCancelled(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        Booking result=bookingService.cancelBooking(1L);

        assertEquals(BookingStatus.CANCELLED,result.getStatus());
    }

    @Test
    @DisplayName("Concurrent simulated claims allow only one winner")
    void concurrentSimulatedClaimsAllowOnlyOne() throws Exception{
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2);
        CountDownLatch start=new CountDownLatch(1);
        AtomicBoolean claimed=new AtomicBoolean(false);

        Callable<Boolean> claim=()->{
            ready.countDown();
            start.await();
            return claimed.compareAndSet(false,true);
        };

        try{
            Future<Boolean> first=executor.submit(claim);
            Future<Boolean> second=executor.submit(claim);

            assertTrue(ready.await(5,TimeUnit.SECONDS));
            start.countDown();

            assertNotEquals(first.get(5,TimeUnit.SECONDS),
                    second.get(5,TimeUnit.SECONDS));
        }finally{
            start.countDown();
            executor.shutdownNow();
        }
    }



    @Test
    @DisplayName("Booking acquires provider lock before checking availability")
    void bookingAcquiresProviderLock(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        Home home=createHome(1L,homeowner);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);

        BookingRequest request=new BookingRequest(1L,1L,date,LocalTime.of(10,0),"AC repair",BookingType.NORMAL);

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date)).thenReturn(Collections.emptyList());
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList())).thenReturn(Collections.emptyList());

        assertThrows(BookingConflictException.class,()->bookingService.createBooking(request));

        InOrder order=inOrder(providerLocks,availabilityRepository);
        order.verify(providerLocks).lockById(2L);
        order.verify(availabilityRepository).findByProviderIdAndDateAndIsAvailableTrue(2L,date);
    }

    @Test
    @DisplayName("Booking rejects a slot reserved by an active waitlist offer")
    void bookingRejectsActiveWaitlistOffer(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        Home home=createHome(1L,homeowner);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);

        Availability available=createAvailability(provider,date);

        BookingWaitlist offer=new BookingWaitlist();
        offer.setService(service);
        offer.setRequestedStartTime(LocalTime.of(10,0));
        offer.setStatus(WaitlistStatus.OFFERED);
        offer.setOfferExpiresAt(LocalDateTime.now().plusMinutes(30));

        BookingRequest request=new BookingRequest(1L,1L,date,LocalTime.of(10,0),"AC repair",BookingType.NORMAL);

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date)).thenReturn(List.of(available));
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList())).thenReturn(Collections.emptyList());
        when(waiting.findByServiceProviderIdAndRequestedDateAndStatus(2L,date,WaitlistStatus.OFFERED)).thenReturn(List.of(offer));

        assertThrows(BookingConflictException.class,()->bookingService.createBooking(request));

        verify(bookingRepository,never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("Cancelling a booking releases its slot to the waitlist")
    void cancelBookingReleasesSlotToWaitlist(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User providerUser=createUser(2L,Role.PROVIDER);
        providerUser.setEmailAddress("provider@gmail.com");

        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setBookingDate(date);
        booking.setStartTime(LocalTime.of(10,0));
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        Booking result=bookingService.cancelBooking(1L);

        assertEquals(BookingStatus.CANCELLED,result.getStatus());
        verify(waitlist).offerNextAvailableProviderSlot(2L,date);
    }

    @Test
    @DisplayName("Booking status is refreshed after acquiring provider lock")
    void bookingStatusRefreshesAfterLock(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(providerUser);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        assertThrows(InvalidBookingStatusException.class, ()->bookingService.updateBookingStatus(1L,BookingStatus.CONFIRMED));

        InOrder order=inOrder(providerLocks,entityManager);
        order.verify(providerLocks).lockById(2L);
        order.verify(entityManager).refresh(booking);
    }

    @Test
    @DisplayName("Booking cannot be completed without a service report")
    void bookingCannotBeCompletedWithoutReport(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);

        Booking booking=new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(providerUser);
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));

        assertThrows(InvalidBookingStatusException.class, ()->bookingService.updateBookingStatus(1L,BookingStatus.COMPLETED));

        verify(bookingRepository,never()).save(any(Booking.class));
    }


    @Test
    @DisplayName("Concurrent bookings for the same provider slot allow only one winner")
    void concurrentBookingsAllowOnlyOneWinner() throws Exception{
        User homeownerA=createUser(1L,Role.HOMEOWNER);
        User homeownerB=createUser(3L,Role.HOMEOWNER);
        Home homeA=createHome(1L,homeownerA);
        Home homeB=createHome(2L,homeownerB);

        User providerUser=createUser(2L,Role.PROVIDER);
        providerUser.setEmailAddress("provider@gmail.com");

        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        service.setPrice(25.0);

        LocalDate date=LocalDate.now().plusDays(3);
        LocalTime time=LocalTime.of(10,0);
        Availability available=createAvailability(provider,date);

        BookingRequest requestA=new BookingRequest(1L,1L,date,time,"Homeowner A",BookingType.NORMAL);
        BookingRequest requestB=new BookingRequest(2L,1L,date,time,"Homeowner B",BookingType.NORMAL);

        ReentrantLock providerLock=new ReentrantLock();
        List<Booking> savedBookings=new CopyOnWriteArrayList<>();
        AtomicLong bookingIds=new AtomicLong(1);

        ThreadLocal<User> currentHomeowner=new ThreadLocal<>();

        when(currentUserService.getCurrentUser()).thenAnswer(invocation->currentHomeowner.get());

        when(homeRepository.findById(1L)).thenReturn(Optional.of(homeA));
        when(homeRepository.findById(2L)).thenReturn(Optional.of(homeB));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        //Simulate provider locking across competing booking requests.
        when(providerLocks.lockById(2L)).thenAnswer(invocation->{
            providerLock.lock();
            return Optional.of(provider);
        });

        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date))
                .thenReturn(List.of(available));

        //Return bookings already saved by the competing thread.
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(
                anyLong(),any(),anyList()
        )).thenAnswer(invocation->List.copyOf(savedBookings));

        //Simulate saving a pending booking.
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation->{
            Booking booking=invocation.getArgument(0);
            booking.setId(bookingIds.getAndIncrement());
            booking.setStatus(BookingStatus.PENDING);
            savedBookings.add(booking);
            return booking;
        });

        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2);
        CountDownLatch start=new CountDownLatch(1);

        //Make both homeowners attempt the same appointment concurrently.
        Callable<Boolean> firstTask=()->{
            currentHomeowner.set(homeownerA);
            ready.countDown();
            try{
                start.await();
                bookingService.createBooking(requestA);
                return true;
            }catch(BookingConflictException e){
                return false;
            }finally{
                if(providerLock.isHeldByCurrentThread()){
                    providerLock.unlock();
                }
                currentHomeowner.remove();
            }
        };

        Callable<Boolean> secondTask=()->{
            currentHomeowner.set(homeownerB);
            ready.countDown();
            try{
                start.await();
                bookingService.createBooking(requestB);
                return true;
            }catch(BookingConflictException e){
                return false;
            }finally{
                if(providerLock.isHeldByCurrentThread()){
                    providerLock.unlock();
                }
                currentHomeowner.remove();
            }
        };

        try{
            Future<Boolean> first=executor.submit(firstTask);
            Future<Boolean> second=executor.submit(secondTask);

            assertTrue(ready.await(5,TimeUnit.SECONDS));
            start.countDown();

            boolean firstResult=first.get(10,TimeUnit.SECONDS);
            boolean secondResult=second.get(10,TimeUnit.SECONDS);

            //Exactly one homeowner must successfully book the slot.
            assertNotEquals(firstResult,secondResult);
            assertEquals(1,savedBookings.size());
            assertEquals(BookingStatus.PENDING,savedBookings.get(0).getStatus());

            verify(bookingRepository).save(any(Booking.class));
        }finally{
            start.countDown();
            executor.shutdownNow();
        }
    }


    @Test
    @DisplayName("Overlapping booking is rejected even with a different start time")
    void overlappingBookingIsRejected(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        Home home=createHome(1L,homeowner);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);

        Availability available=createAvailability(provider,date);

        //Existing appointment occupies 10:00 to 11:00.
        Booking existing=new Booking();
        existing.setStartTime(LocalTime.of(10,0));
        existing.setEndTime(LocalTime.of(11,0));
        existing.setStatus(BookingStatus.PENDING);

        //New appointment overlaps from 10:30 to 11:00.
        BookingRequest request=new BookingRequest(
                1L,1L,date,LocalTime.of(10,30),"Overlapping booking",BookingType.NORMAL
        );

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date))
                .thenReturn(List.of(available));
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList()))
                .thenReturn(List.of(existing));

        assertThrows(BookingConflictException.class,()->bookingService.createBooking(request));

        //The conflicting booking must never be saved.
        verify(bookingRepository,never()).save(any(Booking.class));
    }


    @Test
    @DisplayName("Back-to-back bookings do not conflict")
    void backToBackBookingsAreAllowed(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        homeowner.setEmailAddress("homeowner@gmail.com");
        Home home=createHome(1L,homeowner);

        User providerUser=createUser(2L,Role.PROVIDER);
        providerUser.setEmailAddress("provider@gmail.com");
        ProviderProfile provider=createProvider(2L,providerUser);

        ServiceOffering service=createService(1L,provider);
        service.setPrice(25.0);

        LocalDate date=LocalDate.now().plusDays(3);
        Availability available=createAvailability(provider,date);

        //Existing appointment ends exactly when the new one starts.
        Booking existing=new Booking();
        existing.setStartTime(LocalTime.of(10,0));
        existing.setEndTime(LocalTime.of(11,0));
        existing.setStatus(BookingStatus.CONFIRMED);

        BookingRequest request=new BookingRequest(
                1L,1L,date,LocalTime.of(11,0),"Next appointment",BookingType.NORMAL
        );

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date))
                .thenReturn(List.of(available));
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList()))
                .thenReturn(List.of(existing));

        //Simulate repository persistence.
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation->{
            Booking booking=invocation.getArgument(0);
            booking.setId(10L);
            return booking;
        });

        Booking result=bookingService.createBooking(request);

        assertEquals(LocalTime.of(11,0),result.getStartTime());
        assertEquals(LocalTime.of(12,0),result.getEndTime());
        verify(bookingRepository).save(any(Booking.class));
    }


    @Test
    @DisplayName("Provider lock is acquired before checking existing bookings")
    void providerLockIsAcquiredBeforeCheckingBookingConflicts(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        Home home=createHome(1L,homeowner);
        User providerUser=createUser(2L,Role.PROVIDER);
        ProviderProfile provider=createProvider(2L,providerUser);
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);

        BookingRequest request=new BookingRequest(
                1L,1L,date,LocalTime.of(10,0),"Lock order test",BookingType.NORMAL
        );

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList()))
                .thenReturn(Collections.emptyList());

        assertThrows(BookingConflictException.class,()->bookingService.createBooking(request));

        //Check that locking occurs before reading existing bookings.
        InOrder order=inOrder(providerLocks,bookingRepository);
        order.verify(providerLocks).lockById(2L);
        order.verify(bookingRepository)
                .findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList());
    }

    @Test
    @DisplayName("Inactive service does not hide an authorized homeowner's historical booking")
    void homeownerCanReadHistoricalBookingForInactiveService(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        ServiceOffering service=createService(1L,createProvider(2L,createUser(2L,Role.PROVIDER)));
        service.setName("Previous service");
        service.setStatus(ServiceStatus.INACTIVE);
        Booking booking=new Booking();
        booking.setId(5L);
        booking.setCustomer(homeowner);
        booking.setHome(createHome(3L,homeowner));
        booking.setService(service);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setReportDeadline(LocalDateTime.now().minusDays(1));
        booking.setCreatedAt(LocalDateTime.now().minusDays(4));
        booking.setUpdatedAt(LocalDateTime.now().minusDays(2));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByCustomerId(1L,PageRequest.of(0,10))).thenReturn(new PageImpl<>(List.of(booking)));

        var detail=bookingService.toResponse(bookingService.getBookingById(5L));
        assertEquals("Previous service",detail.serviceName());
        assertEquals(BookingStatus.COMPLETED,detail.status());
        assertEquals(2L,detail.providerId());
        assertEquals(booking.getReportDeadline(),detail.reportDeadline());
        assertEquals(booking.getCreatedAt(),detail.createdAt());
        assertEquals(booking.getUpdatedAt(),detail.updatedAt());
        assertEquals(5L,bookingService.getCurrentUserBookings(null,PageRequest.of(0,10)).getContent().get(0).getId());
    }

    @Test
    void historyReusesParticipantAuthorizationBeforeReadingAuditRecords(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        User provider=createUser(2L,Role.PROVIDER);
        Booking booking=new Booking();
        booking.setCustomer(homeowner);
        booking.setService(createService(1L,createProvider(2L,provider)));
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        PageRequest page=PageRequest.of(0,10);
        when(currentUserService.getCurrentUser()).thenReturn(createUser(3L,Role.HOMEOWNER));
        assertThrows(UnauthorizedException.class,()->bookingService.getBookingHistory(5L,page));
        verify(audit,never()).getBookingHistory(anyLong(),any());
        when(audit.getBookingHistory(5L,page)).thenReturn(new PageImpl<>(List.of(new AuditLog())));
        for(User participant:List.of(homeowner,provider,createUser(4L,Role.ADMIN))){
            when(currentUserService.getCurrentUser()).thenReturn(participant);
            assertEquals(1,bookingService.getBookingHistory(5L,page).getTotalElements());
        }
    }

    @Test
    void discoveryDeduplicatesWindowsAndReadsOccupancyOnlyOnce(){
        ProviderProfile provider=createProvider(2L,createUser(2L,Role.PROVIDER));
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);
        Availability window=createAvailability(provider,date);
        window.setEndTime(LocalTime.of(11,0));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date)).thenReturn(List.of(window,window));

        List<LocalTime> starts=bookingService.getAvailableAppointmentTimes(1L,date,BookingType.NORMAL);
        assertEquals(61,starts.size());
        assertEquals(LocalTime.of(9,0),starts.get(0));
        assertEquals(LocalTime.of(10,0),starts.get(60));
        verify(bookingRepository).findByServiceProviderIdAndBookingDateAndStatusIn(2L,date,
                List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT));
        verify(waiting).findByServiceProviderIdAndRequestedDateAndStatus(2L,date,WaitlistStatus.OFFERED);
        verify(providerLocks,never()).lockById(anyLong());
        verify(bookingRepository,never()).save(any());
    }

    @Test
    void discoveryExcludesBookingsTimeOffAndOffersButAllowsExactAdjacentTimes(){
        ProviderProfile provider=createProvider(2L,createUser(2L,Role.PROVIDER));
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);
        Availability window=createAvailability(provider,date);
        window.setEndTime(LocalTime.of(14,0));
        Availability timeOff=createAvailability(provider,date);
        timeOff.setStartTime(LocalTime.of(12,0));
        timeOff.setEndTime(LocalTime.of(13,0));
        timeOff.setAvailable(false);
        timeOff.setTimeOff(true);
        Booking existing=new Booking();
        existing.setStartTime(LocalTime.of(10,0));
        existing.setEndTime(LocalTime.of(11,0));
        BookingWaitlist offer=new BookingWaitlist();
        offer.setService(service);
        offer.setRequestedStartTime(LocalTime.of(13,0));
        offer.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date)).thenReturn(List.of(window));
        when(availabilityRepository.findByProviderIdAndDateAndTimeOffTrue(2L,date)).thenReturn(List.of(timeOff));
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(),any(),anyList())).thenReturn(List.of(existing));
        when(waiting.findByServiceProviderIdAndRequestedDateAndStatus(2L,date,WaitlistStatus.OFFERED)).thenReturn(List.of(offer));
        assertEquals(List.of(LocalTime.of(9,0),LocalTime.of(11,0)),bookingService.getAvailableAppointmentTimes(1L,date,BookingType.NORMAL));
    }

    @Test
    void discoveryIgnoresExpiredOffersAndHonorsOtherServiceDurations(){
        ProviderProfile provider=createProvider(2L,createUser(2L,Role.PROVIDER));
        ServiceOffering service=createService(1L,provider);
        ServiceOffering longerService=createService(2L,provider);
        longerService.setDurationMinutes(120);
        LocalDate date=LocalDate.now().plusDays(3);
        Availability window=createAvailability(provider,date);
        window.setEndTime(LocalTime.of(12,0));
        BookingWaitlist expired=new BookingWaitlist();
        expired.setService(service);
        expired.setRequestedStartTime(LocalTime.of(9,0));
        expired.setOfferExpiresAt(LocalDateTime.now().minusSeconds(1));
        BookingWaitlist active=new BookingWaitlist();
        active.setService(longerService);
        active.setRequestedStartTime(LocalTime.of(10,0));
        active.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date)).thenReturn(List.of(window));
        when(waiting.findByServiceProviderIdAndRequestedDateAndStatus(2L,date,WaitlistStatus.OFFERED)).thenReturn(List.of(expired,active));
        assertEquals(List.of(LocalTime.of(9,0)),bookingService.getAvailableAppointmentTimes(1L,date,BookingType.NORMAL));
    }

    @Test
    void normalDiscoveryHonorsNoticeAndEmergencyRequiresBothOptIns(){
        ProviderProfile provider=createProvider(2L,createUser(2L,Role.PROVIDER));
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now();
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date)).thenReturn(List.of(createAvailability(provider,date)));
        assertTrue(bookingService.getAvailableAppointmentTimes(1L,date,null).isEmpty());
        assertThrows(BookingConflictException.class,()->bookingService.getAvailableAppointmentTimes(1L,date,BookingType.EMERGENCY));
        service.setEmergencyEnabled(true);
        assertThrows(BookingConflictException.class,()->bookingService.getAvailableAppointmentTimes(1L,date,BookingType.EMERGENCY));
        provider.setEmergencyEnabled(true);
        LocalDate tomorrow=date.plusDays(1);
        Availability early=createAvailability(provider,tomorrow);
        early.setStartTime(LocalTime.MIDNIGHT);
        early.setEndTime(LocalTime.of(1,0));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,tomorrow)).thenReturn(List.of(early));
        assertEquals(List.of(LocalTime.MIDNIGHT),bookingService.getAvailableAppointmentTimes(1L,tomorrow,BookingType.EMERGENCY));
    }

    @Test
    void discoveryRejectsIneligibleServicesAndInvalidDuration(){
        ProviderProfile provider=createProvider(2L,createUser(2L,Role.PROVIDER));
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        service.setStatus(ServiceStatus.INACTIVE);
        assertThrows(BookingConflictException.class,()->bookingService.getAvailableAppointmentTimes(1L,date,null));
        service.setStatus(ServiceStatus.ACTIVE);
        service.getCategory().setStatus(CategoryStatus.INACTIVE);
        assertThrows(BookingConflictException.class,()->bookingService.getAvailableAppointmentTimes(1L,date,null));
        service.getCategory().setStatus(CategoryStatus.ACTIVE);
        provider.setProviderStatus(ProviderStatus.PENDING);
        assertThrows(BookingConflictException.class,()->bookingService.getAvailableAppointmentTimes(1L,date,null));
        provider.setProviderStatus(ProviderStatus.APPROVED);
        provider.getUser().setStatus(UserStatus.INACTIVE);
        assertThrows(BookingConflictException.class,()->bookingService.getAvailableAppointmentTimes(1L,date,null));
        provider.getUser().setStatus(UserStatus.ACTIVE);
        service.setDurationMinutes(1440);
        assertThrows(BookingConflictException.class,()->bookingService.getAvailableAppointmentTimes(1L,date,null));
        assertThrows(IllegalArgumentException.class,()->bookingService.getAvailableAppointmentTimes(1L,date.minusDays(5),null));
    }

    @Test
    void discoveryRejectsMidnightWrapAndRoundsSuggestionsInsideWindow(){
        ProviderProfile provider=createProvider(2L,createUser(2L,Role.PROVIDER));
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);
        Availability window=createAvailability(provider,date);
        window.setStartTime(LocalTime.of(23,0));
        window.setEndTime(LocalTime.of(23,59,59));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date)).thenReturn(List.of(window));
        assertTrue(bookingService.getAvailableAppointmentTimes(1L,date,null).isEmpty());
        service.setDurationMinutes(59);
        assertEquals(List.of(LocalTime.of(23,0)),bookingService.getAvailableAppointmentTimes(1L,date,null));
        window.setStartTime(LocalTime.of(9,0,30));
        window.setEndTime(LocalTime.of(10,0));
        assertEquals(List.of(LocalTime.of(9,1)),bookingService.getAvailableAppointmentTimes(1L,date,null));
    }

    @Test
    void discoveringATimeDoesNotBypassLockedCreationRevalidation(){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        ProviderProfile provider=createProvider(2L,createUser(2L,Role.PROVIDER));
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date))
                .thenReturn(List.of(createAvailability(provider,date)),List.of());
        assertTrue(bookingService.getAvailableAppointmentTimes(1L,date,null).contains(LocalTime.of(9,0)));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(createHome(1L,homeowner)));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        assertThrows(BookingConflictException.class,()->bookingService.createBooking(
                new BookingRequest(1L,1L,date,LocalTime.of(9,0),null,BookingType.NORMAL)));
        verify(providerLocks).lockById(2L);
        verify(bookingRepository,never()).save(any());
    }

    @Test
    void creationRejectsInactiveServiceInitiallyAndAfterDiscovery(){
        assertIneligibleBookingRejected(service->service.setStatus(ServiceStatus.INACTIVE),false);
        assertIneligibleBookingRejected(service->service.setStatus(ServiceStatus.INACTIVE),true);
    }

    @Test
    void creationRejectsInactiveCategoryInitiallyAndAfterDiscovery(){
        assertIneligibleBookingRejected(service->service.getCategory().setStatus(CategoryStatus.INACTIVE),false);
        assertIneligibleBookingRejected(service->service.getCategory().setStatus(CategoryStatus.INACTIVE),true);
    }

    @Test
    void creationRejectsUnapprovedProviderInitiallyAndAfterDiscovery(){
        assertIneligibleBookingRejected(service->service.getProvider().setProviderStatus(ProviderStatus.PENDING),false);
        assertIneligibleBookingRejected(service->service.getProvider().setProviderStatus(ProviderStatus.SUSPENDED),true);
    }

    @Test
    void creationRejectsDeactivatedProviderInitiallyAndAfterDiscovery(){
        assertIneligibleBookingRejected(service->service.getProvider().getUser().setStatus(UserStatus.INACTIVE),false);
        assertIneligibleBookingRejected(service->service.getProvider().getUser().setStatus(UserStatus.INACTIVE),true);
    }

    @Test
    void creationRefreshesEligibilityAfterProviderLock(){
        doAnswer(invocation->{
            if(invocation.getArgument(0) instanceof User user){
                user.setStatus(UserStatus.INACTIVE);
            }
            return null;
        }).when(entityManager).refresh(any());
        assertIneligibleBookingRejected(service->{},false);
        InOrder order=inOrder(providerLocks,entityManager);
        order.verify(providerLocks).lockById(2L);
        order.verify(entityManager).refresh(org.mockito.ArgumentMatchers.isA(ServiceOffering.class));
        order.verify(entityManager).refresh(org.mockito.ArgumentMatchers.isA(Category.class));
        order.verify(entityManager).refresh(org.mockito.ArgumentMatchers.isA(ProviderProfile.class));
        order.verify(entityManager).refresh(org.mockito.ArgumentMatchers.isA(User.class));
    }

    private void assertIneligibleBookingRejected(java.util.function.Consumer<ServiceOffering> change,boolean discoverFirst){
        User homeowner=createUser(1L,Role.HOMEOWNER);
        ProviderProfile provider=createProvider(2L,createUser(2L,Role.PROVIDER));
        ServiceOffering service=createService(1L,provider);
        LocalDate date=LocalDate.now().plusDays(3);
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(createHome(1L,homeowner)));
        when(providerLocks.lockById(2L)).thenReturn(Optional.of(provider));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L,date))
                .thenReturn(List.of(createAvailability(provider,date)));
        if(discoverFirst){
            assertTrue(bookingService.getAvailableAppointmentTimes(1L,date,BookingType.NORMAL).contains(LocalTime.of(9,0)));
        }
        change.accept(service);
        BookingConflictException failure=assertThrows(BookingConflictException.class,()->bookingService.createBooking(
                new BookingRequest(1L,1L,date,LocalTime.of(9,0),null,BookingType.NORMAL)));
        assertEquals("Service unavailable",failure.getMessage());
        verify(bookingRepository,never()).save(any());
    }

    //METHODS

    private User createUser(Long id,Role role){
        User user=new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private Home createHome(Long id,User owner){
        Home home=new Home();
        home.setId(id);
        home.setOwner(owner);
        home.setActive(true);
        return home;
    }

    private ProviderProfile createProvider(Long id,User user){
        ProviderProfile provider=new ProviderProfile();
        provider.setId(id);
        provider.setUser(user);
        provider.setProviderStatus(ProviderStatus.APPROVED);
        return provider;
    }

    private ServiceOffering createService(Long id,ProviderProfile provider){
        ServiceOffering service=new ServiceOffering();
        service.setId(id);
        service.setProvider(provider);
        service.setDurationMinutes(60);
        service.setStatus(ServiceStatus.ACTIVE);
        service.setCategory(new Category());
        return service;
    }
    private Availability createAvailability(ProviderProfile provider,LocalDate date){
        Availability available=new Availability();
        available.setProvider(provider);
        available.setDate(date);
        available.setStartTime(LocalTime.of(9,0));
        available.setEndTime(LocalTime.of(17,0));
        available.setAvailable(true);
        return available;
    }
}
