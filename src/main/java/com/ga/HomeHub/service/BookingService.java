package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.dto.BookingResponse;
import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.InvalidBookingStatusException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.Home;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.*;
import com.ga.HomeHub.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class BookingService {
    private final BookingRepository repository;
    private final HomeRepository homes;
    private final ServiceOfferingRepository services;
    private final AvailabilityRepository availability;
    private final CurrentUserService current;
    private final AuditLogService audit;
    private final NotificationService notification;
    private final EmailService email;

    private final ProviderProfileRepository providerLocks;

    private final ServiceReportRepository reports;
    private final Logger logger = Logger.getLogger(BookingService.class.getName());

    public Booking createBooking(BookingRequest request){
        User user = current.getCurrentUser();
        Home home = homes.findById(request.homeId()).orElseThrow(()-> new InformationNotFoundException("Home not found"));
        ServiceOffering service = services.findById(request.serviceId()).orElseThrow(()-> new InformationNotFoundException("Service not found"));

        providerLocks.lockById(request.homeId()).orElseThrow(()-> new InformationNotFoundException("Provider not found"));
        LocalDateTime startsAt = LocalDateTime.of(request.bookingDate(), request.startTime());
        LocalTime endTime = request.startTime().plusMinutes(service.getDurationMinutes());
        BookingType type = request.bookingType()==null? BookingType.NORMAL : request.bookingType();
        if(!startsAt.isAfter(LocalDateTime.now())){
            throw new BookingConflictException("can not book a past appointment");
        }
        if(type==BookingType.NORMAL && startsAt.isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Normal bookings require at least 24 hours notice");
        }
        if(type==BookingType.EMERGENCY && (!service.isEmergencyEnabled() || !service.getProvider().isEmergencyEnabled())){
            throw new BookingConflictException("Emergency booking is not enabled for this provider and service");
        }
        if(!home.isActive()){
            throw new BookingConflictException("Inactive homes cannot be booked");
        }
        if(!endTime.isAfter(request.startTime())) {
            throw new BookingConflictException("Service must end on the same day");
        }

        boolean blocked = availability.findByProviderIdAndDateAndTimeOffTrue(service.getProvider().getId(),request.bookingDate()).stream().anyMatch(a->request.startTime().isBefore(a.getEndTime()) && endTime.isAfter(a.getStartTime()));
        if(blocked){
            throw new BookingConflictException("Provider is not available");
        }

        boolean slot = availability.findByProviderIdAndDateAndIsAvailableTrue(service.getProvider().getId(), request.bookingDate()).stream().anyMatch(a->a.contains(request.startTime(), endTime));
        boolean clash = repository.findByServiceProviderIdAndBookingDateAndStatusIn(service.getProvider().getId(), request.bookingDate(), List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)).stream().anyMatch(b-> request.startTime().isBefore(b.getEndTime()) && endTime.isAfter(b.getStartTime()));
        Booking booking = new Booking();

        if(request.bookingDate().isBefore(LocalDate.now())){
            throw new BookingConflictException("Can not book in past dates");
        }
        if(!home.getOwner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your home");
        }
        if(service.getStatus() != ServiceStatus.ACTIVE || service.getProvider().getProviderStatus() != ProviderStatus.APPROVED){
            throw new BookingConflictException("Service unavailable");
        }
        if(!slot){
            throw new BookingConflictException("Requested time is outside the provider availability");
        }
        if(clash){
            throw new BookingConflictException("Provider is booked during this time");
        }

        booking.setCustomer(user);
        booking.setHome(home);
        booking.setService(service);
        booking.setBookingDate(request.bookingDate());
        booking.setStartTime(request.startTime());
        booking.setEndTime(endTime);
        booking.setNote(request.note());
        booking.setBookingType(type);
        booking.setFinalPrice(service.getPrice()*(type==BookingType.EMERGENCY?1.20:1.0));
        booking.setResponseDeadline(LocalDateTime.now().plusHours(24).isBefore(startsAt)?LocalDateTime.now().plusHours(24):startsAt);
        booking = repository.save(booking);

        logger.log(Level.INFO, "Booking created successfully");

        audit.record(user,"Create Booking","Booking", booking.getId(), "User created booking");
        sendProviderNotification(booking, "You received a new booking");
        return booking;
    }

    public Page<Booking> getCurrentUserBookings(BookingStatus status, Pageable p){

        User user = current.getCurrentUser();

        if(user.getRole() == Role.PROVIDER){
            if (status == null) {
                return repository.findByServiceProviderUserId(user.getId(), p);
            }
            return repository.findByServiceProviderUserIdAndStatus(user.getId(), status, p);
        }

        if(status == null){
            return repository.findByCustomerId(user.getId(), p);
        }
        return repository.findByCustomerIdAndStatus(user.getId(), status, p);
    }

    public Booking getBookingById(Long id){
        Booking booking = findBookingById(id);
        User user = current.getCurrentUser();
        boolean homeowner = booking.getCustomer().getId().equals(user.getId());
        boolean provider = booking.getService().getProvider().getUser().getId().equals(user.getId());
        if(!homeowner && !provider && user.getRole() != Role.ADMIN){
            throw new UnauthorizedException("You can not view this booking");
        }
        return booking;
    }

    public Booking cancelBooking(Long id){
        Booking booking = findBookingById(id);
        User user = current.getCurrentUser();
        if(!booking.getCustomer().getId().equals(user.getId()) && user.getRole() != Role.ADMIN){
            throw new UnauthorizedException("Not your booking");
        }
        if(booking.getStatus() == BookingStatus.COMPLETED){
            throw new InvalidBookingStatusException("Completed booking can not be cancelled");
        }
        if(booking.getStatus() == BookingStatus.CANCELLED){
            return booking;
        }

        booking.setLateCancellation(LocalDateTime.of(booking.getBookingDate(),booking.getStartTime()).isBefore(LocalDateTime.now().plusHours(24)));
        booking.setStatus(BookingStatus.CANCELLED);
        repository.save(booking);
        logger.log(Level.INFO, "Booking cancelled successfully");
        sendProviderNotification(booking, "Booking cancelled");
        audit.record(user, "Cancel Booking","Booking", booking.getId(), "Booking cancelled");
        return booking;
    }

    public Booking updateBookingStatus(Long id, BookingStatus newStatus){
        Booking booking = findBookingById(id);
        User user = current.getCurrentUser();
        boolean provider = booking.getService().getProvider().getUser().getId().equals(user.getId());
        BookingStatus oldStatus = booking.getStatus();

        if(oldStatus==BookingStatus.PENDING && booking.getResponseDeadline()!=null && LocalDateTime.now().isAfter(booking.getResponseDeadline())){
            throw new InvalidBookingStatusException("Provider response deadline has passed");
        }
        if(newStatus==BookingStatus.COMPLETED && !reports.existsByBookingId(id)){
            throw new InvalidBookingStatusException("Submit the required service report before completing this booking");
        }

        boolean isAvailable = (oldStatus == BookingStatus.PENDING && (newStatus == BookingStatus.CONFIRMED || newStatus == BookingStatus.CANCELLED)) || (oldStatus == BookingStatus.CONFIRMED && (newStatus == BookingStatus.COMPLETED || newStatus == BookingStatus.CANCELLED));

        if(!provider && user.getRole() != Role.ADMIN){
            throw new UnauthorizedException("Provider or admin required");
        }
        if(!isAvailable){
            throw new InvalidBookingStatusException("Invalid booking status transition: "+oldStatus+" -> "+newStatus);
        }

        if(newStatus==BookingStatus.AWAITING_REPORT){
            booking.setReportDeadline(LocalDateTime.of(booking.getBookingDate(),booking.getEndTime()).plusHours(24));
        }

        booking.setStatus(newStatus);
        repository.save(booking);
        logger.log(Level.INFO, "Booking status updated to: " + newStatus);
        audit.record(user,"Update Booking status","Booking", booking.getId(),"Booking status changed to "+newStatus);
        sendBookingNotification(booking, "Booking status changed to "+newStatus);
        return booking;
    }

    private void sendBookingNotification(Booking booking, String message){
        notification.sendNotifications(booking.getCustomer().getId(), message+" (#"+booking.getId()+")");
        email.sendEmail(booking.getCustomer().getEmailAddress(), "HomeHub booking update", message+" for booking #"+booking.getId());
    }

    private void sendProviderNotification(Booking booking, String message) {

        User providerUser = booking.getService().getProvider().getUser();

        notification.sendNotifications(providerUser.getId(), message + " (#" + booking.getId() + ")");

        email.sendEmail(providerUser.getEmailAddress(), "HomeHub new booking", message + " for booking #" + booking.getId());
    }

    private Booking findBookingById(Long id) {
        return repository.findById(id).orElseThrow(() -> new InformationNotFoundException("Booking not found"));
    }

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getHome().getId(),
                booking.getService().getId(),
                booking.getService().getName(),
                booking.getService().getProvider().getBusinessName(),
                booking.getBookingDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getNote(),
                booking.getBookingType().name(),
                booking.getFinalPrice(),
                booking.getResponseDeadline()
        );
    }
}
