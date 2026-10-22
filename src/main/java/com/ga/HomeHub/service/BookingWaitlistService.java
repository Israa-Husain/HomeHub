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
    private final CurrentUserService current;
    private final NotificationService notifications;


    @Transactional
    public BookingWaitlist join(Long serviceId, Long homeId, LocalDate date, LocalTime start){
        User user=current.getCurrentUser();
        Home home=homes.findById(homeId).orElseThrow(()->new InformationNotFoundException("Home not found"));
        if(!home.getOwner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your home");
        }

        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));
        if(!LocalDateTime.of(date,start).isAfter(LocalDateTime.now())){
            throw new BookingConflictException("Waitlist appointment is in the past");
        }

        BookingWaitlist w=new BookingWaitlist();
        w.setHomeowner(user);
        w.setHome(home);
        w.setService(service);
        w.setRequestedDate(date);
        w.setRequestedStartTime(start);
        return waiting.save(w);
    }

    public List<BookingWaitlist> mine(){
        return waiting.findByHomeownerId(current.getCurrentUser().getId());
    }

    //Homeowners can withdraw their own waiting entry.
    @Transactional
    public void leave(Long id){
        BookingWaitlist w=waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));
        if(!w.getHomeowner().getId().equals(current.getCurrentUser().getId())){
            throw new UnauthorizedException("Not your waitlist entry");
        }

        providers.lockById(w.getService().getProvider().getId());
        boolean offered=w.getStatus()== WaitlistStatus.OFFERED;
        w.setStatus(WaitlistStatus.CANCELLED);waiting.save(w);
        if(offered){offerNext(w.getService().getId(),w.getRequestedDate(),w.getRequestedStartTime());}
    }

    //Claims are exclusive, locked, and revalidated against active bookings.
    @Transactional
    public Booking claim(Long id){
        BookingWaitlist w=waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));
        User user=current.getCurrentUser();
        if(!w.getHomeowner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your offer");
        }
        providers.lockById(w.getService().getProvider().getId());
        w=waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Offer not found"));
        if(w.getStatus()!=WaitlistStatus.OFFERED || w.getOfferExpiresAt()==null || !w.getOfferExpiresAt().isAfter(LocalDateTime.now())){
            throw new BookingConflictException("Waitlist offer has expired");
        }

        ServiceOffering service=w.getService();
        LocalTime end=w.getRequestedStartTime().plusMinutes(service.getDurationMinutes());
        if(!end.isAfter(w.getRequestedStartTime())){
            throw new BookingConflictException("Invalid service duration");
        }
        final BookingWaitlist offer=w;
        boolean overlap=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(service.getProvider().getId(),w.getRequestedDate(),
                        List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT)).stream()
                .anyMatch(b->offer.getRequestedStartTime().isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));
        if(overlap){
            throw new BookingConflictException("Appointment is no longer available");
        }

        Booking b=new Booking();b.setCustomer(user);b.setHome(w.getHome());b.setService(service);
        b.setBookingDate(w.getRequestedDate());b.setStartTime(w.getRequestedStartTime());b.setEndTime(end);
        b.setBookingType(BookingType.NORMAL);b.setFinalPrice(service.getPrice());
        b.setResponseDeadline(LocalDateTime.now().plusHours(24));
        b=bookings.save(b);w.setStatus(WaitlistStatus.CLAIMED);waiting.save(w);
        notifications.sendNotifications(service.getProvider().getUser().getId(),"Waitlist offer claimed; booking #"+b.getId());
        return b;
    }

    @Transactional
    public void expireOffers(){
        for(BookingWaitlist w:waiting.findByStatusAndOfferExpiresAtBefore(WaitlistStatus.OFFERED,LocalDateTime.now())){
            providers.lockById(w.getService().getProvider().getId());
            if(w.getStatus()!=WaitlistStatus.OFFERED){
                continue;
            }
            w.setStatus(WaitlistStatus.EXPIRED);waiting.save(w);
            offerNext(w.getService().getId(),w.getRequestedDate(),w.getRequestedStartTime());
        }
    }

    //Lock the provider before offering a released slot.
    @Transactional
    public void offerNext(Long serviceId,LocalDate date,LocalTime start){
        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));
        providers.lockById(service.getProvider().getId());
        List<BookingWaitlist> entries=waiting.findByServiceIdAndRequestedDateAndRequestedStartTimeAndStatusOrderByCreatedAtAsc(serviceId,date,start,WaitlistStatus.WAITING);
        if(!entries.isEmpty()){
            BookingWaitlist w=entries.get(0);w.setStatus(WaitlistStatus.OFFERED);w.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));waiting.save(w);
            notifications.sendNotifications(w.getHomeowner().getId(),"Waitlist slot offered for service #"+serviceId+"; expires in 15 minutes");
        }
    }
}
