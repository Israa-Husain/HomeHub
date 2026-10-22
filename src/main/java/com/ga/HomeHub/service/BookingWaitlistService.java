
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

    //Allows homeowners to join a waitlist for an unavailable appointment.
    @Transactional
    public BookingWaitlist joinWaitlist(Long serviceId, Long homeId, LocalDate date, LocalTime start){
        User user=current.getCurrentUser();
        Home home=homes.findById(homeId).orElseThrow(()->new InformationNotFoundException("Home not found"));

        if(!home.getOwner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your home");
        }

        //Prevent inactive homes from being used for waitlist bookings.
        if(!home.isActive()){
            throw new BookingConflictException("Home is inactive");
        }

        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));

        if(service.getStatus()!= ServiceStatus.ACTIVE || service.getProvider().getProviderStatus()!= ProviderStatus.APPROVED){
            throw new BookingConflictException("Service unavailable");
        }

        //Validate the appointment date and normal booking notice.
        if(date==null || start==null || LocalDateTime.of(date, start).isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Waitlist appointments require at least 24 hours notice");
        }

        //Lock the provider to coordinate waitlist requests with booking operations.
        providers.lockById(service.getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        //Check whether the requested appointment can normally be booked.
        Long providerId=service.getProvider().getId();
        int duration=service.getDurationMinutes();

        if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=86400){
            throw new BookingConflictException("Invalid service duration");
        }

        LocalTime end=start.plusMinutes(duration);

        boolean available=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId,date).stream().anyMatch(a->a.contains(start,end));

        boolean timeOff=availability.findByProviderIdAndDateAndTimeOffTrue(providerId,date).stream().anyMatch(a->start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));

        if(!available || timeOff){
            throw new BookingConflictException("Provider does not work during this appointment");
        }

        //Allow waitlisting only when another booking occupies the appointment.
        boolean overlap=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(providerId,date,List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT)).stream().anyMatch(booking->start.isBefore(booking.getEndTime()) && end.isAfter(booking.getStartTime()));

        if(!overlap){
            throw new BookingConflictException("Appointment is available. Please book it directly");
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

    //Allows a homeowner to withdraw from the waitlist. If an active offer is withdrawn, the next eligible homeowner receives it.
    @Transactional
    public void leaveWaitlist(Long id){
        BookingWaitlist entry = waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));
        User user=current.getCurrentUser();

        if(!entry.getHomeowner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your waitlist entry");
        }

        providers.lockById(entry.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        //Refresh the entry after acquiring the provider lock.
        entityManager.refresh(entry);

        if(entry.getStatus()!=WaitlistStatus.WAITING && entry.getStatus()!=WaitlistStatus.OFFERED){
            throw new BookingConflictException("Waitlist entry is no longer active");
        }

        boolean offered=entry.getStatus()==WaitlistStatus.OFFERED;

        entry.setStatus(WaitlistStatus.CANCELLED);
        waiting.save(entry);

        if(offered){
            offerNextAvailableProviderSlot(entry.getService().getProvider().getId(),entry.getRequestedDate());
        }
    }

    //Allows only the homeowner who received an active offer to claim it. Provider locking prevents simultaneous claims for the same appointment.
    @Transactional
    public Booking claimWaitlistOffer(Long id){
        BookingWaitlist entry=waiting.findById(id).orElseThrow(()->new InformationNotFoundException("Waitlist entry not found"));
        User user=current.getCurrentUser();

        if(user.getStatus()== UserStatus.INACTIVE){
            throw new UnauthorizedException("Your account is deactivated");
        }

        if(!entry.getHomeowner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your offer");
        }

        providers.lockById(entry.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        //Refresh the offer to check its latest status.
        entityManager.refresh(entry);

        //Check the offer again after acquiring the provider lock.
        if(entry.getStatus()!=WaitlistStatus.OFFERED || entry.getOfferExpiresAt()==null || !entry.getOfferExpiresAt().isAfter(LocalDateTime.now())){
            throw new BookingConflictException("Waitlist offer has expired");
        }

        //Enforce the normal booking notice when claiming an offer.
        if(LocalDateTime.of(entry.getRequestedDate(), entry.getRequestedStartTime()).isBefore(LocalDateTime.now().plusHours(24))){
            throw new BookingConflictException("Normal bookings require 24 hours notice");
        }

        if(!entry.getHome().isActive()){
            throw new BookingConflictException("Home is inactive");
        }

        ServiceOffering service=entry.getService();
        LocalTime start=entry.getRequestedStartTime();
        LocalDate date=entry.getRequestedDate();
        Long providerId=service.getProvider().getId();
        int duration=service.getDurationMinutes();

        if(service.getStatus()!=ServiceStatus.ACTIVE || service.getProvider().getProviderStatus()!=ProviderStatus.APPROVED){
            throw new BookingConflictException("Service unavailable");
        }

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

        notifications.sendNotifications(service.getProvider().getUser().getId(), "Waitlist offer claimed; booking #"+booking.getId());
        return booking;
    }

    //Expires old waitlist offers and passes released slots to the next eligible homeowner.
    @Transactional
    public void expireWaitlistOffers(){
        List<BookingWaitlist> expired=waiting.findByStatusAndOfferExpiresAtBefore(
                WaitlistStatus.OFFERED, LocalDateTime.now()
        );

        for(BookingWaitlist entry:expired){
            providers.lockById(entry.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));

            //Refresh the offer before checking whether it has expired.
            entityManager.refresh(entry);

            if(entry.getStatus()!=WaitlistStatus.OFFERED || entry.getOfferExpiresAt()==null || entry.getOfferExpiresAt().isAfter(LocalDateTime.now())){
                continue;
            }

            entry.setStatus(WaitlistStatus.EXPIRED);
            waiting.save(entry);

            offerNextAvailableProviderSlot(entry.getService().getProvider().getId(),entry.getRequestedDate());
        }
    }



    //Offers a released appointment to the first waiting homeowner. Only one active offer may exist for an overlapping provider appointment.
    @Transactional
    public void offerNextAvailableSlot(Long serviceId, LocalDate date, LocalTime start){
        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));
        providers.lockById(service.getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        //Check that the appointment date and time are valid.
        if(date==null || start==null){
            return;
        }

        //Prevent multiple active offers for the same appointment.
        boolean activeOffer=waiting.existsByServiceIdAndRequestedDateAndRequestedStartTimeAndStatus(
                serviceId,date,start,WaitlistStatus.OFFERED
        );

        if(activeOffer){
            return;
        }

        List<BookingWaitlist> entries=waiting.findByServiceIdAndRequestedDateAndRequestedStartTimeAndStatusOrderByCreatedAtAsc(serviceId,date,start,WaitlistStatus.WAITING);

        if(entries.isEmpty()){
            return;
        }

        //Send an offer only while the appointment can still satisfy the 24-hour normal booking notice requirement.
        if(LocalDateTime.of(date,start).isBefore(LocalDateTime.now().plusHours(24))){
            return;
        }

        if(service.getStatus()!=ServiceStatus.ACTIVE || service.getProvider().getProviderStatus()!=ProviderStatus.APPROVED){
            return;
        }

        //Check that the released appointment is still available.
        Long providerId=service.getProvider().getId();
        int duration=service.getDurationMinutes();

        if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=86400){
            return;
        }

        LocalTime end=start.plusMinutes(duration);

        boolean available=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId,date).stream().anyMatch(a->a.contains(start,end));
        boolean timeOff=availability.findByProviderIdAndDateAndTimeOffTrue(providerId,date).stream().anyMatch(a->start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));
        boolean overlap=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(providerId,date,List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT)).stream().anyMatch(booking->start.isBefore(booking.getEndTime()) && end.isAfter(booking.getStartTime()));

        if(!available || timeOff || overlap){
            return;
        }

        //Prevent overlapping offers across different services from the same provider.
        boolean overlappingOffer=waiting.findByServiceProviderIdAndRequestedDateAndStatus(providerId,date,WaitlistStatus.OFFERED)
                .stream().anyMatch(offer->{
                    LocalTime offerStart=offer.getRequestedStartTime();
                    int offerDuration=offer.getService().getDurationMinutes();

                    if(offerDuration<=0){
                        return true;
                    }

                    if(offerStart.toSecondOfDay()+(long)offerDuration*60>=86400){
                        return true;
                    }

                    LocalTime offerEnd=offerStart.plusMinutes(offerDuration);

                    return start.isBefore(offerEnd) && end.isAfter(offerStart);
                });

        if(overlappingOffer){
            return;
        }

        //Offer the appointment to the first eligible homeowner.
        for(BookingWaitlist entry:entries){
            if(!entry.getHome().isActive()){
                entry.setStatus(WaitlistStatus.CANCELLED);
                waiting.save(entry);
                continue;
            }

            entry.setStatus(WaitlistStatus.OFFERED);
            entry.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));
            waiting.save(entry);

            notifications.sendNotifications(entry.getHomeowner().getId(), "Waitlist slot offered for service #"+serviceId+"; expires in 15 minutes");
            return;
        }
    }

    //Checks all waiting appointments for a provider and offers eligible non-overlapping slots in FIFO order.
    @Transactional
    public void offerNextAvailableProviderSlot(Long providerId, LocalDate date){
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        if(date==null){
            return;
        }

        //Retrieve waiting homeowners across all services in the order they joined.
        List<BookingWaitlist> entries=waiting.findByServiceProviderIdAndRequestedDateAndStatusOrderByCreatedAtAsc(providerId,date,WaitlistStatus.WAITING);

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

            //Check availability, time off, and existing bookings.
            boolean available=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId,date).stream().anyMatch(a->a.contains(start,end));
            boolean timeOff=availability.findByProviderIdAndDateAndTimeOffTrue(providerId,date).stream().anyMatch(a->start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));
            boolean overlap=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(providerId,date,List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT)).stream().anyMatch(booking->start.isBefore(booking.getEndTime()) && end.isAfter(booking.getStartTime()));

            if(!available || timeOff || overlap){
                continue;
            }

            //Prevent overlapping offers across all services belonging to this provider.
            boolean overlappingOffer=waiting.findByServiceProviderIdAndRequestedDateAndStatus(providerId,date,WaitlistStatus.OFFERED).stream().anyMatch(offer->{
                        LocalTime offerStart=offer.getRequestedStartTime();
                        int offerDuration=offer.getService().getDurationMinutes();

                        if(offerDuration<=0 || offerStart.toSecondOfDay()+(long)offerDuration*60>=86400){
                            return true;
                        }

                        LocalTime offerEnd=offerStart.plusMinutes(offerDuration);
                        return start.isBefore(offerEnd) && end.isAfter(offerStart);
            });

            if(overlappingOffer){
                continue;
            }

            entry.setStatus(WaitlistStatus.OFFERED);
            entry.setOfferExpiresAt(LocalDateTime.now().plusMinutes(15));
            waiting.save(entry);

            notifications.sendNotifications(entry.getHomeowner().getId(),"Waitlist slot offered for service #"+service.getId()+"; expires in 15 minutes");
        }
    }
}
