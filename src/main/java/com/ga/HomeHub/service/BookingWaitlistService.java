
package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.*;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.model.enums.BookingType;
import com.ga.HomeHub.model.enums.WaitlistStatus;
import com.ga.HomeHub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingWaitlistService {
    private final BookingWaitlistRepository waiting;
    private final ServiceOfferingRepository services;
    private final HomeRepository homes;
    private final BookingRepository bookings;
    private final ProviderProfileRepository providers;
    private final AvailabilityRepository availability;
    private final CurrentUserService current;
    private final NotificationService notifications;

    //Allows homeowners to join a waitlist for an unavailable appointment.
    @Transactional
    public BookingWaitlist join(Long serviceId, Long homeId, LocalDate date, LocalTime start){
        User user=current.getCurrentUser();
        Home home=homes.findById(homeId).orElseThrow(()->new InformationNotFoundException("Home not found"));

        if(!home.getOwner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your home");
        }

        //Prevent inactive homes from being used for waitlist bookings.
        if(!home.isActive()){
            throw new BookingConflictException("Home is inactive");
        }

        ServiceOffering service=services.findById(serviceId)
                .orElseThrow(()->new InformationNotFoundException("Service not found"));

        //Validate the appointment date and normal booking notice.
        if(date==null || start==null || LocalDateTime.of(date, start).isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Waitlist appointments require 24 hours notice");
        }

        //Prevent duplicate active waitlist entries for the same appointment.
        boolean duplicate=waiting.findByHomeownerId(user.getId()).stream()
                .anyMatch(w-> w.getService().getId().equals(serviceId)
                                && w.getHome().getId().equals(homeId)
                                && w.getRequestedDate().equals(date)
                                && w.getRequestedStartTime().equals(start)
                                && (w.getStatus()==WaitlistStatus.WAITING || w.getStatus()==WaitlistStatus.OFFERED));

        if(duplicate){
            throw new BookingConflictException("You are already on the waitlist for this appointment");
        }

        BookingWaitlist w=new BookingWaitlist();
        w.setHomeowner(user);
        w.setHome(home);
        w.setService(service);
        w.setRequestedDate(date);
        w.setRequestedStartTime(start);
        return waiting.save(w);
    }

    //Returns only the current homeowner's waitlist entries.
    public List<BookingWaitlist> mine(){
        return waiting.findByHomeownerId(current.getCurrentUser().getId());
    }

    //Allows a homeowner to withdraw from the waitlist. If an active offer is withdrawn, the next eligible homeowner receives it.
    @Transactional
    public void leave(Long id){
        BookingWaitlist w=waiting.findById(id)
                .orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));

        if(!w.getHomeowner().getId().equals(current.getCurrentUser().getId())){
            throw new UnauthorizedException("Not your waitlist entry");
        }

        providers.lockById(w.getService().getProvider().getId());

        //Reload the entry after acquiring the provider lock.
        w=waiting.findById(id)
                .orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));

        if(w.getStatus()!=WaitlistStatus.WAITING && w.getStatus()!=WaitlistStatus.OFFERED){
            throw new BookingConflictException("Waitlist entry is no longer active");
        }

        boolean offered=w.getStatus()==WaitlistStatus.OFFERED;

        w.setStatus(WaitlistStatus.CANCELLED);
        waiting.save(w);

        if(offered){
            offerNext(w.getService().getId(), w.getRequestedDate(), w.getRequestedStartTime());
        }
    }

    //Allows only the homeowner who received an active offer to claim it. Provider locking prevents simultaneous claims for the same appointment.
    @Transactional
    public Booking claim(Long id){
        BookingWaitlist w=waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));
        User user=current.getCurrentUser();
        if(!w.getHomeowner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your offer");
        }
        providers.lockById(w.getService().getProvider().getId());

        //Reload the offer after locking to check its latest status.
        w=waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Offer not found"));

        if(w.getStatus()!=WaitlistStatus.OFFERED
                || w.getOfferExpiresAt()==null
                || !w.getOfferExpiresAt().isAfter(LocalDateTime.now())){
            throw new BookingConflictException("Waitlist offer has expired");
        }

        //Enforce the normal booking notice when claiming an offer.
        if(LocalDateTime.of(w.getRequestedDate(), w.getRequestedStartTime()).isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Normal bookings require 24 hours notice");
        }

        if(!w.getHome().isActive()){
            throw new BookingConflictException("Home is inactive");
        }

        ServiceOffering service=w.getService();
        LocalTime start=w.getRequestedStartTime();
        LocalDate date=w.getRequestedDate();
        Long providerId=service.getProvider().getId();
        int duration=service.getDurationMinutes();

        //Reject appointments with invalid durations or those crossing midnight.
        if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=24*60*60){
            throw new BookingConflictException("Invalid service duration");
        }

        LocalTime end=start.plusMinutes(duration);

        //Verify that the provider is available for the requested time.
        boolean available=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId, date).stream().anyMatch(a->a.contains(start, end));

        //Reject claims that overlap provider time off.
        boolean timeOff=availability.findByProviderIdAndDateAndTimeOffTrue(providerId, date).stream().anyMatch(a->start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));

        //Recheck existing bookings while holding the provider lock.
        boolean overlap=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(providerId, date, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.AWAITING_REPORT)).stream().anyMatch(b->start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));

        if(!available || timeOff || overlap){
            throw new BookingConflictException("Appointment is no longer available");
        }

        Booking b=new Booking();
        b.setCustomer(user);
        b.setHome(w.getHome());
        b.setService(service);
        b.setBookingDate(date);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setBookingType(BookingType.NORMAL);
        b.setFinalPrice(service.getPrice());
        b.setResponseDeadline(LocalDateTime.now().plusHours(24));
        b=bookings.save(b);

        w.setStatus(WaitlistStatus.CLAIMED);
        waiting.save(w);

        notifications.sendNotifications(service.getProvider().getUser().getId(), "Waitlist offer claimed; booking #"+b.getId());
        return b;
    }

    //Expires old waitlist offers and passes released slots to the next eligible homeowner.
    @Transactional
    public void expireOffers(){
        List<BookingWaitlist> expired=waiting.findByStatusAndOfferExpiresAtBefore(
                WaitlistStatus.OFFERED, LocalDateTime.now()
        );

        for(BookingWaitlist entry:expired){
            providers.lockById(entry.getService().getProvider().getId());

            //Reload the offer after locking so a claimed offer not incorrectly expired by a concurrent scheduled task.
            BookingWaitlist w=waiting.findById(entry.getId()).orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));

            if(w.getStatus()!=WaitlistStatus.OFFERED || w.getOfferExpiresAt()==null || w.getOfferExpiresAt().isAfter(LocalDateTime.now())){
                continue;
            }

            w.setStatus(WaitlistStatus.EXPIRED);
            waiting.save(w);
            offerNext(w.getService().getId(), w.getRequestedDate(), w.getRequestedStartTime());
        }
    }

    //Offers a released appointment to the first waiting homeowner. Only one active offer may exist for the same service, date, and start time.
    @Transactional
    public void offerNext(Long serviceId, LocalDate date, LocalTime start){
        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));
        providers.lockById(service.getProvider().getId());

        //Prevent multiple active offers for the same appointment.
        boolean activeOffer=waiting.existsByServiceIdAndRequestedDateAndRequestedStartTimeAndStatus(
                serviceId, date, start, WaitlistStatus.OFFERED
        );

        if(activeOffer){
            return;
        }

        List<BookingWaitlist> entries=waiting.findByServiceIdAndRequestedDateAndRequestedStartTimeAndStatusOrderByCreatedAtAsc(serviceId, date, start, WaitlistStatus.WAITING);

        if(entries.isEmpty()){
            return;
        }

        //Send an offer only while the appointment can still satisfy the 24-hour normal booking notice requirement.
        if(LocalDateTime.of(date, start).isBefore(LocalDateTime.now().plusHours(24))){
            return;
        }

        BookingWaitlist w=entries.get(0);
        w.setStatus(WaitlistStatus.OFFERED);
        w.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));
        waiting.save(w);

        notifications.sendNotifications(w.getHomeowner().getId(), "Waitlist slot offered for service #"+serviceId+"; expires in 15 minutes");
    }
}
