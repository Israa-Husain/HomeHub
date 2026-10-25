
package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.BookingWaitlist;
import com.ga.HomeHub.model.Home;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.model.enums.BookingType;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.model.enums.ServiceStatus;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.model.enums.WaitlistStatus;
import com.ga.HomeHub.repository.AvailabilityRepository;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.BookingWaitlistRepository;
import com.ga.HomeHub.repository.HomeRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import com.ga.HomeHub.repository.ServiceOfferingRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    //Allows homeowners to join a waitlist for an unavailable appointment.
    @Transactional
    public BookingWaitlist joinWaitlist(Long serviceId, Long homeId, LocalDate date, LocalTime start){
        User user=current.getCurrentUser();
        Home home=homes.findById(homeId).orElseThrow(()->new InformationNotFoundException("Home not found"));

        if(!home.getOwner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your home");
        }

        if(user.getStatus()==UserStatus.INACTIVE || !home.isActive()){
            throw new BookingConflictException("Homeowner or home is inactive");
        }

        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));

        if(service.getStatus()!=ServiceStatus.ACTIVE || service.getProvider().getProviderStatus()!=ProviderStatus.APPROVED){
            throw new BookingConflictException("Service unavailable");
        }

        //Validate the appointment date and normal booking notice.
        if(date==null || start==null || LocalDateTime.of(date,start).isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Waitlist appointments require at least 24 hours notice");
        }

        //Lock the provider before checking availability.
        Long providerId=service.getProvider().getId();
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        int duration=service.getDurationMinutes();

        if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=86400){
            throw new BookingConflictException("Invalid service duration");
        }

        LocalTime end=start.plusMinutes(duration);

        boolean available=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId,date).stream()
                .anyMatch(a->a.contains(start,end));

        boolean timeOff=availability.findByProviderIdAndDateAndTimeOffTrue(providerId,date).stream()
                .anyMatch(a->start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));

        if(!available || timeOff){
            throw new BookingConflictException("Provider does not work during this appointment");
        }

        boolean overlap=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(
                        providerId,date,List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT))
                .stream().anyMatch(b->start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));

        if(!overlap && !hasOverlappingOffer(providerId,date,start,end)){
            throw new BookingConflictException("Appointment is available. Please book it directly");
        }

        //Prevent duplicate active waitlist entries.
        boolean duplicate=waiting.findByHomeownerId(user.getId()).stream()
                .anyMatch(w->w.getService().getId().equals(serviceId)
                        && w.getHome().getId().equals(homeId)
                        && w.getRequestedDate().equals(date)
                        && w.getRequestedStartTime().equals(start)
                        && (w.getStatus()==WaitlistStatus.WAITING || w.getStatus()==WaitlistStatus.OFFERED));

        if(duplicate){
            throw new BookingConflictException("You are already on the waitlist for this appointment");
        }

        BookingWaitlist entry=new BookingWaitlist();
        entry.setHomeowner(user);
        entry.setHome(home);
        entry.setService(service);
        entry.setRequestedDate(date);
        entry.setRequestedStartTime(start);
        entry.setStatus(WaitlistStatus.WAITING);

        return waiting.save(entry);
    }

    //Returns only the current homeowner's waitlist entries.
    @Transactional(readOnly=true)
    public List<BookingWaitlist> getMyWaitlist(){
        return waiting.findByHomeownerId(current.getCurrentUser().getId());
    }

    //Allows a homeowner to withdraw from the waitlist.
    @Transactional
    public void leaveWaitlist(Long id){
        BookingWaitlist entry=waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));
        User user=current.getCurrentUser();

        if(!entry.getHomeowner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your waitlist entry");
        }

        Long providerId=entry.getService().getProvider().getId();
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        entityManager.refresh(entry);

        if(entry.getStatus()!=WaitlistStatus.WAITING && entry.getStatus()!=WaitlistStatus.OFFERED){
            throw new BookingConflictException("Waitlist entry is no longer active");
        }

        boolean offered=entry.getStatus()==WaitlistStatus.OFFERED;

        entry.setStatus(WaitlistStatus.CANCELLED);
        waiting.save(entry);

        if(offered){
            offerNextAvailableProviderSlot(providerId,entry.getRequestedDate());
        }
    }

    //Allows the homeowner to claim an active waitlist offer.
    @Transactional
    public Booking claimWaitlistOffer(Long id){
        BookingWaitlist entry=waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));
        User user=current.getCurrentUser();

        if(user.getStatus()==UserStatus.INACTIVE){
            throw new UnauthorizedException("Your account is deactivated");
        }

        if(!entry.getHomeowner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your offer");
        }

        Long providerId=entry.getService().getProvider().getId();
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        entityManager.refresh(entry);

        if(entry.getStatus()!=WaitlistStatus.OFFERED || entry.getOfferExpiresAt()==null
                || !entry.getOfferExpiresAt().isAfter(LocalDateTime.now())){
            throw new BookingConflictException("Waitlist offer has expired");
        }

        if(LocalDateTime.of(entry.getRequestedDate(),entry.getRequestedStartTime()).isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Normal bookings require 24 hours notice");
        }

        if(!entry.getHome().isActive() || !entry.getHome().getOwner().getId().equals(user.getId())){
            throw new BookingConflictException("Home is inactive or does not belong to you");
        }

        ServiceOffering service=entry.getService();
        LocalDate date=entry.getRequestedDate();
        LocalTime start=entry.getRequestedStartTime();
        int duration=service.getDurationMinutes();

        if(service.getStatus()!=ServiceStatus.ACTIVE || service.getProvider().getProviderStatus()!=ProviderStatus.APPROVED){
            throw new BookingConflictException("Service unavailable");
        }

        if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=86400){
            throw new BookingConflictException("Invalid service duration");
        }

        LocalTime end=start.plusMinutes(duration);

        boolean available=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId,date).stream()
                .anyMatch(a->a.contains(start,end));

        boolean timeOff=availability.findByProviderIdAndDateAndTimeOffTrue(providerId,date).stream()
                .anyMatch(a->start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));

        boolean overlap=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(
                        providerId,date,List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT))
                .stream().anyMatch(b->start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));

        if(!available || timeOff || overlap){
            throw new BookingConflictException("Appointment is no longer available");
        }

        Booking booking=new Booking();
        booking.setCustomer(user);
        booking.setHome(entry.getHome());
        booking.setService(service);
        booking.setBookingDate(date);
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setBookingType(BookingType.NORMAL);
        booking.setFinalPrice(service.getPrice());
        booking.setStatus(BookingStatus.PENDING);

        LocalDateTime deadline=LocalDateTime.now().plusHours(24);
        LocalDateTime appointmentStart=LocalDateTime.of(date,start);

        if(deadline.isAfter(appointmentStart)){
            deadline=appointmentStart;
        }

        booking.setResponseDeadline(deadline);
        booking=bookings.save(booking);

        entry.setStatus(WaitlistStatus.CLAIMED);
        waiting.save(entry);

        notifications.sendNotifications(service.getProvider().getUser().getId(),
                "Waitlist offer claimed; booking #"+booking.getId());

        return booking;
    }

    //Expires old offers in separate transactions to avoid holding multiple provider locks.
    public void expireWaitlistOffers(){
        List<Long> expiredIds=waiting.findByStatusAndOfferExpiresAtBefore(
                        WaitlistStatus.OFFERED,LocalDateTime.now())
                .stream().map(BookingWaitlist::getId).toList();

        TransactionTemplate transaction=new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        for(Long id:expiredIds){
            transaction.execute(status->{
                BookingWaitlist entry=waiting.findById(id).orElse(null);

                if(entry==null){
                    return null;
                }

                Long providerId=entry.getService().getProvider().getId();
                providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));

                entityManager.refresh(entry);

                if(entry.getStatus()!=WaitlistStatus.OFFERED || entry.getOfferExpiresAt()==null
                        || entry.getOfferExpiresAt().isAfter(LocalDateTime.now())){
                    return null;
                }

                entry.setStatus(WaitlistStatus.EXPIRED);
                waiting.save(entry);

                offerNextAvailableProviderSlot(providerId,entry.getRequestedDate());

                return null;
            });
        }
    }

    //Uses the provider-wide queue instead of offering only to one service.
    @Transactional
    public void offerNextAvailableSlot(Long serviceId, LocalDate date, LocalTime start){
        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));

        if(date==null || start==null){
            return;
        }

        offerNextAvailableProviderSlot(service.getProvider().getId(),date);
    }

    //Offers available appointments across all provider services in FIFO order.
    @Transactional
    public void offerNextAvailableProviderSlot(Long providerId, LocalDate date){
        if(date==null){
            return;
        }

        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        List<BookingWaitlist> entries=waiting.findByServiceProviderIdAndRequestedDateAndStatusOrderByCreatedAtAscIdAsc(
                providerId,date,WaitlistStatus.WAITING);

        for(BookingWaitlist entry:entries){
            if(!entry.getHome().isActive() || entry.getHomeowner().getStatus()==UserStatus.INACTIVE){
                entry.setStatus(WaitlistStatus.CANCELLED);
                waiting.save(entry);
                continue;
            }

            ServiceOffering service=entry.getService();

            if(service.getStatus()!=ServiceStatus.ACTIVE || service.getProvider().getProviderStatus()!=ProviderStatus.APPROVED){
                continue;
            }

            LocalTime start=entry.getRequestedStartTime();
            int duration=service.getDurationMinutes();

            if(LocalDateTime.of(date,start).isBefore(LocalDateTime.now().plusHours(24))){
                continue;
            }

            if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=86400){
                continue;
            }

            LocalTime end=start.plusMinutes(duration);

            boolean available=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId,date).stream()
                    .anyMatch(a->a.contains(start,end));

            boolean timeOff=availability.findByProviderIdAndDateAndTimeOffTrue(providerId,date).stream()
                    .anyMatch(a->start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));

            boolean overlap=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(
                            providerId,date,List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT))
                    .stream().anyMatch(b->start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));

            if(!available || timeOff || overlap){
                continue;
            }

            if(hasOverlappingOffer(providerId,date,start,end)){
                continue;
            }

            entry.setStatus(WaitlistStatus.OFFERED);
            entry.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));
            waiting.save(entry);

            notifications.sendNotifications(entry.getHomeowner().getId(),
                    "Waitlist slot offered for service #"+service.getId()+"; expires in 15 minutes");
        }
    }

    //Checks active offers across all services belonging to the provider.
    private boolean hasOverlappingOffer(Long providerId, LocalDate date, LocalTime start, LocalTime end){
        return waiting.findByServiceProviderIdAndRequestedDateAndStatus(providerId,date,WaitlistStatus.OFFERED)
                .stream().anyMatch(offer->{
                    if(offer.getOfferExpiresAt()==null || !offer.getOfferExpiresAt().isAfter(LocalDateTime.now())){
                        return false;
                    }

                    LocalTime offerStart=offer.getRequestedStartTime();
                    int duration=offer.getService().getDurationMinutes();

                    if(duration<=0 || offerStart.toSecondOfDay()+(long)duration*60>=86400){
                        return true;
                    }

                    LocalTime offerEnd=offerStart.plusMinutes(duration);

                    return start.isBefore(offerEnd) && end.isAfter(offerStart);
                });
    }
}
