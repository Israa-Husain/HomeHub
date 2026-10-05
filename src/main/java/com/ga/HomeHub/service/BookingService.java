package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.InvalidBookingStatusException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.Home;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.ServiceStatus;
import com.ga.HomeHub.repository.AvailabilityRepository;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.HomeRopository;
import com.ga.HomeHub.repository.ServiceOfferingRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@AllArgsConstructor
public class BookingService {
    private final BookingRepository repository;
    private final HomeRopository homes;
    private final ServiceOfferingRepository services;
    private final AvailabilityRepository availability;
    private final CurrentUserService current;
    private final AuditLogService audit;
    private final NotificationService notification;
    private final EmailService email;


    public Booking createBooking(BookingRequest request){
        User user = current.getCurrentUser();
        Home home = homes.findById(request.homeId()).orElseThrow(()-> new InformationNotFoundException("Home not found"));
        ServiceOffering service = services.findById(request.serviceId()).orElseThrow(()-> new InformationNotFoundException("Service not found"));
        LocalTime endTime = request.startTime().plusMinutes(service.getDurationMinutes());
        boolean slot = availability.findByProviderIdAndDate(service.getProvider().getId(), request.bookingDate()).stream().anyMatch(a->a.contains(request.startTime(), endTime));
        boolean clash = repository.findServiceProviderIdAndBookingDateAndStatusIn(service.getProvider().getId(), request.bookingDate(), List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)).stream().anyMatch(b-> request.startTime().isBefore(b.getEndTime()) && endTime.isAfter(b.getStartTime()));
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
        booking = repository.save(booking);

        audit.record(user,"Create Booking","Booking", booking.getId(), "User created booking");

        return booking;
    }

    public Page<Booking> getCurrentUserBookings(BookingStatus status, Pageable p){
        Long id = current.getCurrentUser().getId();
        return status == null? repository.findByCustomerId(id,p) : repository.findByCustomerIdAndStatus(id, status, p);
    }

    public Booking getBookingById(Long id){
        return repository.findById(id).orElseThrow(()->new InformationNotFoundException("Booking not found"));
    }

    public Booking cancelBooking(Long id){
        Booking booking = getBookingById(id);
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

        booking.setStatus(BookingStatus.CANCELLED);
        repository.save(booking);
        sendBookingNotification(booking, "Booking cancelled");
        audit.record(user, "Cancel Booking","Booking", booking.getId(), "Booking cancelled");
        return booking;
    }

    public Booking updateBookingStatus(Long id, BookingStatus newStatus){
        Booking booking = getBookingById(id);
        User user = current.getCurrentUser();
        boolean provider = booking.getService().getProvider().getUser().getId().equals(user.getId());
        BookingStatus oldStatus = booking.getStatus();
        boolean isAvailable = (oldStatus == BookingStatus.PENDING && (newStatus == BookingStatus.CONFIRMED || newStatus == BookingStatus.CANCELLED)) || (oldStatus == BookingStatus.CONFIRMED && (newStatus == BookingStatus.COMPLETED || newStatus == BookingStatus.CANCELLED));

        if(!provider && user.getRole() != Role.ADMIN){
            throw new UnauthorizedException("Provider or admin required");
        }
        if(!isAvailable){
            throw new InvalidBookingStatusException("Invalid booking status transaction: "+oldStatus+" -> "+newStatus);
        }

        booking.setStatus(newStatus);
        repository.save(booking);
        sendBookingNotification(booking, "Booking status changed to "+newStatus);
        return booking;
    }

    private void sendBookingNotification(Booking booking, String message){
        notification.sendNotifications(booking.getCustomer().getId(), message+" (#"+booking.getId()+")");
        email.sendEmail(booking.getCustomer().getEmailAddress(), "HomeHub booking update", message+" for booking #"+booking.getId());
    }
}
