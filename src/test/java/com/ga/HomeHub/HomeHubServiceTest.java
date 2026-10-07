package com.ga.HomeHub;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.dto.auth.LoginRequest;
import com.ga.HomeHub.dto.auth.RegisterRequest;
import com.ga.HomeHub.exception.*;
import com.ga.HomeHub.model.*;
import com.ga.HomeHub.model.enums.*;
import com.ga.HomeHub.repository.*;
import com.ga.HomeHub.service.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;



@ExtendWith(MockitoExtension.class)
public class HomeHubServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private HomeRopository homeRepository;

    @Mock
    private ServiceOfferingRepository serviceRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private CurrentUserService currentUserService;


    @InjectMocks
    private AuthService authService;

    @InjectMocks
    private BookingService bookingService;

    @Test
    @DisplayName("Login fails when email is not verified")
    void loginFailsWhenEmailIsNotVerified() {
        User user = new User();
        user.setEmailAddress("user@gmail.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        when(userRepository.findUserByEmailAddress("user@gmail.com")).thenReturn(user);

        LoginRequest request = new LoginRequest("user@gmail.com", "Password123");

        assertThrows(UnauthorizedException.class, () -> authService.loginUser(request));
    }

    @Test
    @DisplayName("Inactive user cannot login")
    void inactiveUserCannotLogin() {

        User user = new User();
        user.setEmailAddress("user@gmail.com");
        user.setStatus(UserStatus.INACTIVE);
        user.setEmailVerified(true);

        when(userRepository.findUserByEmailAddress("user@gmail.com")).thenReturn(user);

        LoginRequest request = new LoginRequest("user@gmail.com", "Password123");

        assertThrows(UnauthorizedException.class, () -> authService.loginUser(request));
    }

    @Test
    @DisplayName("Registration rejects an invalid email")
    void registerRequestRejectsInvalidEmail() {
        RegisterRequest request = new RegisterRequest("Sara", "Ahmed", "not-an-email", "Password123", "33333333", Role.HOMEOWNER);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<RegisterRequest>> errors = validator.validate(request);

        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("Registration rejects a password shorter than 8 characters")
    void registerRequestRejectsShortPassword() {
        RegisterRequest request = new RegisterRequest("Sara", "Ahmed", "sara@gmail.com", "123", "33333333", Role.HOMEOWNER);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<RegisterRequest>> errors = validator.validate(request);

        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("Booking fails when requested time is outside provider availability")
    void bookingFailsWhenTimeIsOutsideProviderAvailability() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        Home home = createHome(1L, homeowner);
        User providerUser = createUser(2L, Role.PROVIDER);
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);
        LocalDate date = LocalDate.now().plusDays(1);

        BookingRequest request = new BookingRequest(1L, 1L, date, LocalTime.of(10, 0), "AC repair");

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L, date)).thenReturn(Collections.emptyList());
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(), any(), anyList())).thenReturn(Collections.emptyList());

        assertThrows(BookingConflictException.class, () -> bookingService.createBooking(request));
    }

    @Test
    @DisplayName("Booking fails when provider is already booked")
    void bookingFailsWhenProviderIsAlreadyBooked() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        Home home = createHome(1L, homeowner);
        User providerUser = createUser(2L, Role.PROVIDER);
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);
        LocalDate date = LocalDate.now().plusDays(1);

        Availability available = new Availability();
        available.setProvider(provider);
        available.setDate(date);
        available.setStartTime(LocalTime.of(9, 0));
        available.setEndTime(LocalTime.of(17, 0));
        available.setAvailable(true);

        Booking existingBooking = new Booking();
        existingBooking.setStartTime(LocalTime.of(10, 0));
        existingBooking.setEndTime(LocalTime.of(11, 0));
        existingBooking.setStatus(BookingStatus.CONFIRMED);

        BookingRequest request = new BookingRequest(1L, 1L, date, LocalTime.of(10, 30), "AC repair");

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L, date)).thenReturn(Collections.singletonList(available));
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(), any(), anyList())).thenReturn(Collections.singletonList(existingBooking));

        assertThrows(BookingConflictException.class, () -> bookingService.createBooking(request));
    }

    @Test
    @DisplayName("Completed booking cannot be cancelled")
    void completedBookingCannotBeCancelled() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);

        assertThrows(InvalidBookingStatusException.class, () -> bookingService.cancelBooking(1L));
    }

    @Test
    @DisplayName("Homeowner can cancel their own booking")
    void homeownerCanCancelOwnBooking() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        homeowner.setEmailAddress("homeowner@gmail.com");
        User providerUser = createUser(2L, Role.PROVIDER);
        providerUser.setEmailAddress("provider@gmail.com");
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);

        Booking result = bookingService.cancelBooking(1L);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
    }

    @Test
    @DisplayName("Homeowner cannot update booking status")
    void homeownerCannotUpdateBookingStatus() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        User providerUser = createUser(2L, Role.PROVIDER);
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);

        assertThrows(UnauthorizedException.class, () -> bookingService.updateBookingStatus(1L, BookingStatus.CONFIRMED));
    }

    @Test
    @DisplayName("User cannot cancel another user's booking")
    void userCannotCancelAnotherUsersBooking() {
        User owner = createUser(1L, Role.HOMEOWNER);
        User otherUser = createUser(2L, Role.HOMEOWNER);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(owner);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(otherUser);

        assertThrows(UnauthorizedException.class, () -> bookingService.cancelBooking(1L));
    }

    @Test
    @DisplayName("Booking cannot be created for a past date")
    void bookingCannotBeCreatedInThePast() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        Home home = createHome(1L, homeowner);
        User providerUser = createUser(2L, Role.PROVIDER);
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);
        LocalDate pastDate = LocalDate.now().minusDays(1);

        BookingRequest request = new BookingRequest(1L, 1L, pastDate, LocalTime.of(10, 0), "AC repair");

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L, pastDate)).thenReturn(Collections.emptyList());
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(), any(), anyList())).thenReturn(Collections.emptyList());

        assertThrows(BookingConflictException.class, () -> bookingService.createBooking(request));
    }

    @Test
    @DisplayName("User cannot create a booking for another user's home")
    void userCannotBookAnotherUsersHome() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        User otherOwner = createUser(2L, Role.HOMEOWNER);
        Home home = createHome(1L, otherOwner);
        User providerUser = createUser(3L, Role.PROVIDER);
        ProviderProfile provider = createProvider(3L, providerUser);
        ServiceOffering service = createService(1L, provider);
        LocalDate date = LocalDate.now().plusDays(1);

        BookingRequest request = new BookingRequest(1L, 1L, date, LocalTime.of(10, 0), "Cleaning");

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(3L, date)).thenReturn(Collections.emptyList());
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(), any(), anyList())).thenReturn(Collections.emptyList());

        assertThrows(UnauthorizedException.class, () -> bookingService.createBooking(request));
    }

    @Test
    @DisplayName("Inactive service cannot be booked")
    void inactiveServiceCannotBeBooked() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        Home home = createHome(1L, homeowner);
        User providerUser = createUser(2L, Role.PROVIDER);
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);
        service.setStatus(ServiceStatus.INACTIVE);
        LocalDate date = LocalDate.now().plusDays(1);

        BookingRequest request = new BookingRequest(1L, 1L, date, LocalTime.of(10, 0), "Cleaning");

        when(currentUserService.getCurrentUser()).thenReturn(homeowner);
        when(homeRepository.findById(1L)).thenReturn(Optional.of(home));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(availabilityRepository.findByProviderIdAndDateAndIsAvailableTrue(2L, date)).thenReturn(Collections.emptyList());
        when(bookingRepository.findByServiceProviderIdAndBookingDateAndStatusIn(anyLong(), any(), anyList())).thenReturn(Collections.emptyList());

        assertThrows(BookingConflictException.class, () -> bookingService.createBooking(request));
    }

    @Test
    @DisplayName("Pending booking can be confirmed")
    void pendingBookingCanBeConfirmed() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        homeowner.setEmailAddress("homeowner@gmail.com");
        User providerUser = createUser(2L, Role.PROVIDER);
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(providerUser);

        Booking result = bookingService.updateBookingStatus(1L, BookingStatus.CONFIRMED);

        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

// =======================================

    @Test
    @DisplayName("Duplicate email cannot be registered")
    void duplicateEmailCannotRegister() {
        RegisterRequest request = new RegisterRequest("Sara", "Ahmed", "sara@gmail.com", "Password123", "33333333", Role.HOMEOWNER);

        when(userRepository.existsByEmailAddress("sara@gmail.com")).thenReturn(true);

        assertThrows(InformationExistException.class, () -> authService.registerUser(request));
    }

    @Test
    @DisplayName("Admin cannot self-register")
    void adminCannotSelfRegister() {
        RegisterRequest request = new RegisterRequest("Admin", "User", "admin@gmail.com", "Password123", "33333333", Role.ADMIN);

        when(userRepository.existsByEmailAddress("admin@gmail.com")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.registerUser(request));
    }

    @Test
    @DisplayName("Cancelled booking cannot be confirmed")
    void cancelledBookingCannotBeConfirmed() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        User providerUser = createUser(2L, Role.PROVIDER);
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(providerUser);

        assertThrows(InvalidBookingStatusException.class, () -> bookingService.updateBookingStatus(1L, BookingStatus.CONFIRMED));
    }

    @Test
    @DisplayName("Confirmed booking can be completed")
    void confirmedBookingCanBeCompleted() {
        User homeowner = createUser(1L, Role.HOMEOWNER);
        homeowner.setEmailAddress("homeowner@gmail.com");
        User providerUser = createUser(2L, Role.PROVIDER);
        ProviderProfile provider = createProvider(2L, providerUser);
        ServiceOffering service = createService(1L, provider);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setService(service);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(providerUser);

        Booking result = bookingService.updateBookingStatus(1L, BookingStatus.COMPLETED);

        assertEquals(BookingStatus.COMPLETED, result.getStatus());
    }

    @Test
    @DisplayName("Cancelling an already cancelled booking keeps it cancelled")
    void cancellingAlreadyCancelledBookingKeepsItCancelled() {
        User homeowner = createUser(1L, Role.HOMEOWNER);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(homeowner);
        booking.setStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(currentUserService.getCurrentUser()).thenReturn(homeowner);

        Booking result = bookingService.cancelBooking(1L);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
    }

// METHODS

    private User createUser(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private Home createHome(Long id, User owner) {
        Home home = new Home();
        home.setId(id);
        home.setOwner(owner);
        return home;
    }

    private ProviderProfile createProvider(Long id, User user) {
        ProviderProfile provider = new ProviderProfile();
        provider.setId(id);
        provider.setUser(user);
        provider.setProviderStatus(ProviderStatus.APPROVED);
        return provider;
    }

    private ServiceOffering createService(Long id, ProviderProfile provider) {
        ServiceOffering service = new ServiceOffering();
        service.setId(id);
        service.setProvider(provider);
        service.setDurationMinutes(60);
        service.setStatus(ServiceStatus.ACTIVE);
        return service;
    }


}
