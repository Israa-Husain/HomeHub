package com.ga.HomeHub.booking.service;
import com.ga.HomeHub.booking.client.AccountDirectory;
import com.ga.HomeHub.booking.config.BookingPolicyProperties;
import com.ga.HomeHub.booking.validation.ProviderScheduleValidator;
import com.ga.HomeHub.booking.validation.ServiceEligibilityValidator;
import org.springframework.transaction.annotation.Propagation;


import com.ga.HomeHub.booking.exception.BookingConflictException;
import com.ga.HomeHub.booking.exception.InformationNotFoundException;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.BookingWaitlist;
import com.ga.HomeHub.booking.model.Home;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.BookingType;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import com.ga.HomeHub.booking.model.enums.WaitlistStatus;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.BookingWaitlistRepository;
import com.ga.HomeHub.booking.repository.HomeRepository;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import com.ga.HomeHub.booking.repository.ServiceOfferingRepository;
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
import java.util.logging.Logger;
import java.util.logging.Level;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingWaitlistService {
    //One schedule validator is shared by every provider schedule mutation.
    private final ProviderScheduleValidator schedule;
    private final BookingPolicyProperties policy;
    private final AccountDirectory accounts;
    private final ServiceEligibilityValidator eligibility;
    private final BookingWaitlistRepository waiting;
    private final ServiceOfferingRepository services;
    private final HomeRepository homes;
    private final BookingRepository bookings;
    private final ProviderProfileRepository providers;
    private final CurrentUserService current;
    private final AuditLogService audit;
    private final NotificationService notifications;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private static final Logger logger = Logger.getLogger(BookingWaitlistService.class.getName());

    //Allows homeowners to join a waitlist for an unavailable appointment.
    @Transactional
    public BookingWaitlist joinWaitlist(Long serviceId, Long homeId, LocalDate date, LocalTime start){
        User user=current.getCurrentUser();
        //Track new waitlist requests without logging personal details.
        logger.info("Waitlist join requested: userId="+user.getId()+", serviceId="+serviceId+", date="+date+", start="+start);
        Home home=homes.findById(homeId).orElseThrow(()->new InformationNotFoundException("Home not found"));

        if(!home.getOwner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your home");
        }

        if(user.getStatus()==UserStatus.INACTIVE || !home.isActive()){
            throw new BookingConflictException("Homeowner or home is inactive");
        }

        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));


        //Validate the appointment date and normal booking notice.
        if(date==null || start==null || LocalDateTime.of(date,start).isBefore(LocalDateTime.now().plusHours(policy.getNormalNoticeHours()))){
            throw new BookingConflictException("Waitlist appointments require at least 24 hours notice");
        }

        //Trace provider locking when investigating simultaneous waitlist operations.
        logger.fine("Waiting for provider lock: providerId="+service.getProvider().getId());
        //Lock the provider before checking availability.
        Long providerId=service.getProvider().getId();
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        logger.fine("Provider lock acquired: providerId="+providerId);
        eligibility.refreshAndRequire(service);

        int duration=service.getDurationMinutes();

        if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=86400){
            throw new BookingConflictException("Invalid service duration");
        }

        LocalTime end=start.plusMinutes(duration);

        if(!schedule.withinWorkingHours(providerId,date,start,end) || schedule.hasTimeOff(providerId,date,start,end)){
            throw new BookingConflictException("Provider does not work during this appointment");
        }
        boolean overlap=schedule.hasBookingConflict(providerId,date,start,end,null);

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
            //Record duplicate active waitlist requests.
            logger.warning("Duplicate waitlist request: userId="+user.getId()+", serviceId="+serviceId);
            throw new BookingConflictException("You are already on the waitlist for this appointment");
        }

        BookingWaitlist entry=new BookingWaitlist();
        entry.setHomeowner(user);
        entry.setHome(home);
        entry.setService(service);
        entry.setRequestedDate(date);
        entry.setRequestedStartTime(start);
        entry.setStatus(WaitlistStatus.WAITING);
        entry=waiting.save(entry);
        logger.info("Waitlist entry created: entryId="+entry.getId()+", providerId="+providerId);
        return entry;
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
        //Trace provider locking when investigating simultaneous waitlist operations.
        logger.fine("Waiting for provider lock: providerId="+providerId);
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        logger.fine("Provider lock acquired: providerId="+providerId);

        entityManager.refresh(entry);

        if(entry.getStatus()!=WaitlistStatus.WAITING && entry.getStatus()!=WaitlistStatus.OFFERED){
            throw new BookingConflictException("Waitlist entry is no longer active");
        }

        boolean offered=entry.getStatus()==WaitlistStatus.OFFERED;

        entry.setStatus(WaitlistStatus.CANCELLED);
        waiting.save(entry);
        //Record waitlist withdrawal and whether an offered slot was released.
        logger.info("Waitlist entry cancelled: entryId="+id+", releasedOffer="+offered);
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
        //Trace provider locking when investigating simultaneous waitlist operations.
        logger.fine("Waiting for provider lock: providerId="+providerId);
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        logger.fine("Provider lock acquired: providerId="+providerId);

        entityManager.refresh(entry);

        if(entry.getStatus()!=WaitlistStatus.OFFERED || entry.getOfferExpiresAt()==null
                || !entry.getOfferExpiresAt().isAfter(LocalDateTime.now())){
            //Record failed attempts to claim expired or inactive offers.
            logger.warning("Waitlist claim rejected: inactive or expired offer, entryId="+id);
            throw new BookingConflictException("Waitlist offer has expired");
        }

        if(LocalDateTime.of(entry.getRequestedDate(),entry.getRequestedStartTime()).isBefore(LocalDateTime.now().plusHours(policy.getNormalNoticeHours()))){
            throw new BookingConflictException("Normal bookings require 24 hours notice");
        }

        if(!entry.getHome().isActive() || !entry.getHome().getOwner().getId().equals(user.getId())){
            throw new BookingConflictException("Home is inactive or does not belong to you");
        }

        ServiceOffering service=entry.getService();
        eligibility.refreshAndRequire(service);
        entityManager.refresh(entry.getHome());
        if(!entry.getHome().isActive()){throw new BookingConflictException("Home is inactive");}
        LocalDate date=entry.getRequestedDate();
        LocalTime start=entry.getRequestedStartTime();
        int duration=service.getDurationMinutes();


        if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=86400){
            throw new BookingConflictException("Invalid service duration");
        }

        LocalTime end=start.plusMinutes(duration);

        //An offer reserves this interval for its owner, so exclude only the claimed offer.
        schedule.requireAvailable(providerId,date,start,end,null,entry.getId());

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

        LocalDateTime deadline=LocalDateTime.now().plusHours(policy.getResponseDeadlineHours());
        LocalDateTime appointmentStart=LocalDateTime.of(date,start);

        if(deadline.isAfter(appointmentStart)){
            deadline=appointmentStart;
        }

        booking.setResponseDeadline(deadline);
        booking=bookings.save(booking);
        audit.record(user,"Create Booking","Booking",booking.getId(),"Created from claimed waitlist offer #"+entry.getId());

        entry.setStatus(WaitlistStatus.CLAIMED);
        waiting.save(entry);

        //Record the claimed offer and the booking it created.
        logger.info("Waitlist offer claimed: entryId="+id+", bookingId="+booking.getId());

        notifications.sendNotifications(service.getProvider().getUser().getId(),
                "Waitlist offer claimed; booking #"+booking.getId());

        return booking;
    }

    //Expire each offer in its own transaction and hold only one provider lock at a time.
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void expireWaitlistOffers(){
        List<Long> expiredIds=waiting.findByStatusAndOfferExpiresAtBefore(
                        WaitlistStatus.OFFERED,LocalDateTime.now())
                .stream().map(BookingWaitlist::getId).toList();

        //Log the number of expired offer candidates.
        logger.info("Waitlist expiration scan: expiredOfferCandidates="+expiredIds.size());
        TransactionTemplate transaction=new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        for(Long id:expiredIds){
            try{
                transaction.execute(status->{
                    BookingWaitlist entry=waiting.findById(id).orElse(null);

                    if(entry==null){
                        return null;
                    }

                    Long providerId=entry.getService().getProvider().getId();
                    //Trace provider locking when investigating simultaneous waitlist operations.
                    logger.fine("Waiting for provider lock: providerId="+providerId);
                    providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));
                    logger.fine("Provider lock acquired: providerId="+providerId);

                    entityManager.refresh(entry);

                    if(entry.getStatus()!=WaitlistStatus.OFFERED || entry.getOfferExpiresAt()==null
                            || entry.getOfferExpiresAt().isAfter(LocalDateTime.now())){
                        return null;
                    }

                    entry.setStatus(WaitlistStatus.EXPIRED);
                    waiting.save(entry);

                    //Record each offer expiration inside its own transaction.
                    logger.info("Waitlist offer expired: entryId="+id+", providerId="+providerId);

                    offerNextAvailableProviderSlot(providerId,entry.getRequestedDate());

                    return null;
                });
            }catch(RuntimeException e){
                logger.log(Level.WARNING,"Waitlist expiration candidate failed: "+id,e);
            }
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

        //Trace provider locking when investigating simultaneous waitlist operations.
        logger.fine("Waiting for provider lock: providerId="+providerId);
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        logger.fine("Provider lock acquired: providerId="+providerId);

        List<BookingWaitlist> entries=waiting.findByServiceProviderIdAndRequestedDateAndStatusOrderByCreatedAtAscIdAsc(
                providerId,date,WaitlistStatus.WAITING);

        for(BookingWaitlist entry:entries){
            entityManager.refresh(entry);
            entityManager.refresh(entry.getHome());
            if(entry.getStatus()!=WaitlistStatus.WAITING){
                continue;
            }
            if(!entry.getHome().isActive() || !accounts.isActive(entry.getHomeowner().getId())){
                entry.setStatus(WaitlistStatus.CANCELLED);
                waiting.save(entry);
                continue;
            }

            ServiceOffering service=entry.getService();

            try{eligibility.refreshAndRequire(service);}
            catch(BookingConflictException unavailable){continue;}

            LocalTime start=entry.getRequestedStartTime();
            int duration=service.getDurationMinutes();

            if(LocalDateTime.of(date,start).isBefore(LocalDateTime.now().plusHours(policy.getNormalNoticeHours()))){
                continue;
            }

            if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=86400){
                continue;
            }

            LocalTime end=start.plusMinutes(duration);

            if(!schedule.isAvailable(providerId,date,start,end,null,null)){
                continue;
            }

            entry.setStatus(WaitlistStatus.OFFERED);
            LocalDateTime now=LocalDateTime.now();
            LocalDateTime latestClaim=LocalDateTime.of(date,start).minusHours(policy.getNormalNoticeHours());
            if(!latestClaim.isAfter(now)){
                continue;
            }
            LocalDateTime expires=now.plusMinutes(policy.getWaitlistOfferMinutes());
            entry.setOfferExpiresAt(expires.isBefore(latestClaim)?expires:latestClaim);
            waiting.save(entry);

            //Record the time-limited offer for troubleshooting queue behavior.
            logger.info("Waitlist offer created: entryId="+entry.getId()+", providerId="+providerId+", expiresAt="+entry.getOfferExpiresAt());

            notifications.sendNotifications(entry.getHomeowner().getId(),
                    "Waitlist slot offered for service #"+service.getId()+"; expires at "+entry.getOfferExpiresAt());
        }
    }

    //Checks active offers across all services belonging to the provider.
    private boolean hasOverlappingOffer(Long providerId, LocalDate date, LocalTime start, LocalTime end){
        return schedule.hasOffer(providerId,date,start,end,null);
    }
}