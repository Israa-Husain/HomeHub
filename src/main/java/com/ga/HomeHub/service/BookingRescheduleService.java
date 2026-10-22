
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

    //Allows a homeowner to request a different appointment without changing the original booking until the provider approves.
    @Transactional
    public BookingRescheduleRequest request(Long bookingId, LocalDate date, LocalTime start){
        Booking b=bookings.findById(bookingId).orElseThrow(()->new InformationNotFoundException("Booking not found"));
        User user=current.getCurrentUser();
        if(!b.getCustomer().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your booking");
        }
        if(b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.CONFIRMED){
            throw new BookingConflictException("Booking can not be rescheduled");
        }
        if(requests.existsByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING)){
            throw new BookingConflictException("An active request already exists");
        }

        //Require at least 24 hours notice for the new appointment.
        if(date==null || start==null || LocalDateTime.of(date, start).isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Rescheduling requires 24 hours notice");
        }

        BookingRescheduleRequest r=new BookingRescheduleRequest();
        r.setBooking(b);
        r.setRequester(user);
        r.setRequestedDate(date);
        r.setRequestedStartTime(start);
        r=requests.save(r);
        audit.record(user, "Request Reschedule", "Booking", bookingId, "Requested "+date+" "+start);
        return r;
    }

    //Allows the assigned provider to approve or reject a request. Approval rechecks the appointment while holding the provider database lock.
    @Transactional
    public BookingRescheduleRequest decide(Long id, boolean approve){
        BookingRescheduleRequest r=requests.findById(id).orElseThrow(()->new InformationNotFoundException("Reschedule request not found"));
        Booking b=r.getBooking();
        User user=current.getCurrentUser();
        if(!b.getService().getProvider().getUser().getId().equals(user.getId())){
            throw new UnauthorizedException("Not assigned provider");
        }

        //Lock the provider before validating or changing the booking to prevent concurrent bookings and rescheduling conflicts.
        providers.lockById(b.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        if(r.getStatus()!=RescheduleStatus.PENDING){
            throw new BookingConflictException("Request already decided");
        }

        //A booking may have changed status while the request was pending.
        if(b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.CONFIRMED){
            throw new BookingConflictException("Booking can no longer be rescheduled");
        }
        if(approve){
            LocalDate date=r.getRequestedDate();
            LocalTime start=r.getRequestedStartTime();

            //Recheck the 24-hour notice rule at approval time.
            if(date==null || start==null || LocalDateTime.of(date, start).isBefore(LocalDateTime.now().plusHours(24))){
                throw new BookingConflictException("Rescheduling requires 24 hours notice");
            }

            int duration=b.getService().getDurationMinutes();

            //Reject appointments that would end after midnight because HomeHub stores appointment start and end on the same date.
            if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=24*60*60){
                throw new BookingConflictException("Invalid rescheduled appointment time");
            }

            LocalTime end=start.plusMinutes(duration);
            Long providerId=b.getService().getProvider().getId();
            boolean slot=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId, date).stream().anyMatch(a->a.contains(start, end));
            boolean clash=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(providerId, date, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.AWAITING_REPORT)).stream().anyMatch(other-> !other.getId().equals(b.getId()) && start.isBefore(other.getEndTime()) && end.isAfter(other.getStartTime()));

            //Check whether the requested appointment overlaps any time-off period blocked by the provider.
            boolean timeOff=availability.findByProviderIdAndDateAndTimeOffTrue(providerId, date).stream().anyMatch(a-> start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));

            if(!slot || clash || timeOff){
                throw new BookingConflictException("Requested slot no longer available");
            }

            //Only update the original booking after the provider approves and all availability and concurrency checks succeed.
            b.setBookingDate(date);
            b.setStartTime(start);
            b.setEndTime(end);
            // Reset reminders when an appointment is rescheduled so the homeowner and provider receive reminders for the new date.
            b.setReminder24Sent(false);
            b.setReminder1Sent(false);
            bookings.save(b);
        }

        r.setStatus(approve?RescheduleStatus.APPROVED:RescheduleStatus.REJECTED);
        audit.record(user, "Reschedule Decision", "Booking", b.getId(), approve?"Approved":"Rejected");
        return requests.save(r);
    }
}