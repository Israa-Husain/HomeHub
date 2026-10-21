package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.BookingRescheduleRequest;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.model.enums.RescheduleStatus;
import com.ga.HomeHub.repository.AvailabilityRepository;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.BookingRescheduleRequestRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingRescheduleService {
    private final BookingRescheduleRequestRepository requests;
    private final BookingRepository bookings;
    private final ProviderProfileRepository providers;
    private final AvailabilityRepository availability;
    private final CurrentUserService current;
    private final AuditLogService audit;

    @Transactional
    public BookingRescheduleRequest request(Long bookingId, LocalDate date, LocalTime start){
        Booking b=bookings.findById(bookingId).orElseThrow(()->new InformationNotFoundException("Booking not found"));
        User user=current.getCurrentUser();
        if(!b.getCustomer().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your booking");
        }
        if(b.getStatus()!= BookingStatus.PENDING && b.getStatus()!=BookingStatus.CONFIRMED){
            throw new BookingConflictException("Booking can not be rescheduled");
        }
        if(requests.existsByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING)){
            throw new BookingConflictException("An active request already exists");
        }
        if(LocalDateTime.of(date,start).isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Rescheduling requires 24 hours notice");
        }

        BookingRescheduleRequest r=new BookingRescheduleRequest();
        r.setBooking(b);
        r.setRequester(user);
        r.setRequestedDate(date);
        r.setRequestedStartTime(start);
        r=requests.save(r);
        audit.record(user,"Request Reschedule","Booking",bookingId,"Requested "+date+" "+start);
        return r;
    }

    @Transactional
    public BookingRescheduleRequest decide(Long id,boolean approve){
        BookingRescheduleRequest r=requests.findById(id).orElseThrow(()->new InformationNotFoundException("Reschedule request not found"));
        Booking b=r.getBooking();User user=current.getCurrentUser();
        if(!b.getService().getProvider().getUser().getId().equals(user.getId())){
            throw new UnauthorizedException("Not assigned provider");
        }

        providers.lockById(b.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        if(r.getStatus()!=RescheduleStatus.PENDING){
            throw new BookingConflictException("Request already decided");
        }
        if(approve){
            LocalTime end=r.getRequestedStartTime().plusMinutes(b.getService().getDurationMinutes());
            boolean slot=availability.findByProviderIdAndDateAndIsAvailableTrue(b.getService().getProvider().getId(),r.getRequestedDate()).stream().anyMatch(a->a.contains(r.getRequestedStartTime(),end));
            boolean clash=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(b.getService().getProvider().getId(),r.getRequestedDate(), List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT)).stream().anyMatch(other->!other.getId().equals(b.getId()) && r.getRequestedStartTime().isBefore(other.getEndTime()) && end.isAfter(other.getStartTime()));
            if(!slot || clash){
                throw new BookingConflictException("Requested slot no longer available");
            }
            b.setBookingDate(r.getRequestedDate());
            b.setStartTime(r.getRequestedStartTime());
            b.setEndTime(end);bookings.save(b);
        }

        r.setStatus(approve?RescheduleStatus.APPROVED:RescheduleStatus.REJECTED);
        audit.record(user,"Reschedule Decision","Booking",b.getId(),approve?"Approved":"Rejected");
        return requests.save(r);
    }
}