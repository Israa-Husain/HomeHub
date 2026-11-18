package com.ga.HomeHub.booking.validation;
import com.ga.HomeHub.booking.model.BookingWaitlist;

import com.ga.HomeHub.booking.exception.BookingConflictException;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.WaitlistStatus;
import com.ga.HomeHub.booking.repository.AvailabilityRepository;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.BookingWaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProviderScheduleValidator{
    private final AvailabilityRepository availability;
    private final BookingRepository bookings;
    private final BookingWaitlistRepository waiting;

    //Call after obtaining the provider lock. Adjacent appointments do not overlap.
    public boolean overlaps(LocalTime start,LocalTime end,LocalTime otherStart,LocalTime otherEnd){
        return start.isBefore(otherEnd) && end.isAfter(otherStart);
    }

    public boolean hasBookingConflict(Long providerId,LocalDate date,LocalTime start,LocalTime end,Long excludedBookingId){
        return bookings.findByServiceProviderIdAndBookingDateAndStatusIn(providerId,date,
                        List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT))
                .stream().anyMatch(b->(excludedBookingId==null || !excludedBookingId.equals(b.getId())) && overlaps(start,end,b.getStartTime(),b.getEndTime()));
    }

    public boolean withinWorkingHours(Long providerId,LocalDate date,LocalTime start,LocalTime end){
        return availability.findByProviderIdAndDateAndIsAvailableTrue(providerId,date).stream().anyMatch(a->a.contains(start,end));
    }

    public boolean hasTimeOff(Long providerId,LocalDate date,LocalTime start,LocalTime end){
        return availability.findByProviderIdAndDateAndTimeOffTrue(providerId,date).stream().anyMatch(a->overlaps(start,end,a.getStartTime(),a.getEndTime()));
    }

    public boolean hasOffer(Long providerId,LocalDate date,LocalTime start,LocalTime end,Long excludedOfferId){
        return hasOffer(waiting.findByServiceProviderIdAndRequestedDateAndStatus(providerId,date,WaitlistStatus.OFFERED),start,end,excludedOfferId);
    }

    public boolean hasOffer(List<BookingWaitlist> offers,LocalTime start,LocalTime end,Long excludedOfferId){
        return offers.stream().anyMatch(w->{
            if((excludedOfferId!=null && excludedOfferId.equals(w.getId())) || w.getOfferExpiresAt()==null || !w.getOfferExpiresAt().isAfter(LocalDateTime.now())){return false;}
            int minutes=w.getService().getDurationMinutes();
            if(minutes<=0 || w.getRequestedStartTime().toSecondOfDay()+(long)minutes*60>=86400){return true;}
            return overlaps(start,end,w.getRequestedStartTime(),w.getRequestedStartTime().plusMinutes(minutes));
        });
    }

    public boolean isAvailable(Long providerId,LocalDate date,LocalTime start,LocalTime end,Long excludedBookingId,Long excludedOfferId){
        boolean slot=withinWorkingHours(providerId,date,start,end);
        boolean timeOff=hasTimeOff(providerId,date,start,end);
        boolean conflict=hasBookingConflict(providerId,date,start,end,excludedBookingId);
        boolean offer=hasOffer(providerId,date,start,end,excludedOfferId);
        return slot && !timeOff && !conflict && !offer;
    }

    //The caller retains its transaction and lock until the business change commits.
    public void requireAvailable(Long providerId,LocalDate date,LocalTime start,LocalTime end,Long excludedBookingId,Long excludedOfferId){
        if(!isAvailable(providerId,date,start,end,excludedBookingId,excludedOfferId)){
            throw new BookingConflictException("Requested appointment is no longer available");
        }
    }
}