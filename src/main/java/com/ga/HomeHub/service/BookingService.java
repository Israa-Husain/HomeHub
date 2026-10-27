
package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.dto.BookingResponse;
import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.InvalidBookingStatusException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.*;
import com.ga.HomeHub.model.enums.*;
import com.ga.HomeHub.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
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
    private final BookingWaitlistService waitlist;
    private final Logger logger=Logger.getLogger(BookingService.class.getName());
    private final BookingWaitlistRepository waiting;
    private final EntityManager entityManager;

    @Transactional
    public Booking createBooking(BookingRequest request){
        User user=current.getCurrentUser();
        Home home=homes.findById(request.homeId()).orElseThrow(()->new InformationNotFoundException("Home not found"));
        ServiceOffering service=services.findById(request.serviceId()).orElseThrow(()->new InformationNotFoundException("Service not found"));

        //Log the booking request.
        logger.info("Booking requested: userId="+user.getId()+", serviceId="+service.getId()+", date="+request.bookingDate()+", startTime="+request.startTime());

        //Log provider lock acquisition to help trace concurrent requests.
        logger.fine("Waiting for provider lock: providerId="+service.getProvider().getId());

        //Lock the correct provider before checking appointment availability.
        providerLocks.lockById(service.getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        //Log when the provider lock has been acquired.
        logger.fine("Provider lock acquired: providerId="+service.getProvider().getId());

        LocalDateTime startsAt=LocalDateTime.of(request.bookingDate(),request.startTime());
        LocalTime endTime=request.startTime().plusMinutes(service.getDurationMinutes());
        BookingType type=request.bookingType()==null?BookingType.NORMAL:request.bookingType();
        if(!startsAt.isAfter(LocalDateTime.now())){
            logger.warning("Booking rejected: reason=Past appointment , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Can not book a past appointment");
        }
        if(type==BookingType.NORMAL && startsAt.isBefore(LocalDateTime.now().plusHours(24))){
            logger.warning("Booking rejected: reason=Insufficient notice , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Normal bookings require at least 24 hours notice");
        }
        if(type==BookingType.EMERGENCY && (!service.isEmergencyEnabled() || !service.getProvider().isEmergencyEnabled())){
            logger.warning("Booking rejected: reason=Emergency booking disabled , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Emergency booking is not enabled for this provider and service");
        }
        if(!home.isActive()){
            logger.warning("Booking rejected: reason=Inactive home , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Inactive homes cannot be booked");
        }
        if(service.getDurationMinutes()<=0 || request.startTime().toSecondOfDay()+(long)service.getDurationMinutes()*60>=86400){
            logger.warning("Booking rejected: reason=Invalid service end time , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Service must end on the same day");
        }

        boolean blocked=availability.findByProviderIdAndDateAndTimeOffTrue(service.getProvider().getId(),request.bookingDate()).stream()
                .anyMatch(a->request.startTime().isBefore(a.getEndTime()) && endTime.isAfter(a.getStartTime()));

        if(blocked){
            logger.warning("Booking rejected: reason=Provider time off , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Provider is not available");
        }

        boolean slot=availability.findByProviderIdAndDateAndIsAvailableTrue(service.getProvider().getId(),request.bookingDate()).stream().anyMatch(a->a.contains(request.startTime(),endTime));

        boolean clash=repository.findByServiceProviderIdAndBookingDateAndStatusIn(
                service.getProvider().getId(),
                request.bookingDate(),
                List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT)
        ).stream().anyMatch(b->request.startTime().isBefore(b.getEndTime()) && endTime.isAfter(b.getStartTime()));

        if(!home.getOwner().getId().equals(user.getId())){
            logger.warning("Booking denied: userId="+user.getId()+", homeId="+home.getId());
            throw new UnauthorizedException("Not your home");
        }
        if(service.getStatus()!=ServiceStatus.ACTIVE || service.getProvider().getProviderStatus()!=ProviderStatus.APPROVED){
            logger.warning("Booking rejected: unavailable serviceId="+service.getId()+", providerId="+service.getProvider().getId());
            throw new BookingConflictException("Service unavailable");
        }
        if(!slot){
            logger.warning("Booking rejected: reason=Outside provider availability , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Requested time is outside the provider availability");
        }
        if(clash){
            logger.warning("Booking rejected: reason=Overlapping provider booking , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Provider is booked during this time");
        }

        if(hasOverlappingOffer(service.getProvider().getId(),request.bookingDate(),request.startTime(),endTime)){
            logger.warning("Booking rejected: reason=Active waitlist offer , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Appointment has an active waitlist offer");
        }

        Booking booking=new Booking();
        booking.setCustomer(user);
        booking.setHome(home);
        booking.setService(service);
        booking.setBookingDate(request.bookingDate());
        booking.setStartTime(request.startTime());
        booking.setEndTime(endTime);
        booking.setNote(request.note());
        booking.setBookingType(type);
        booking.setFinalPrice(service.getPrice()*(type==BookingType.EMERGENCY?1.20:1.0));
        LocalDateTime now=LocalDateTime.now();
        booking.setResponseDeadline(now.plusHours(24).isBefore(startsAt)?now.plusHours(24):startsAt);
        booking=repository.save(booking);

        //Log the saved booking for tracing; the transaction may not have committed yet.
        logger.info("Booking saved: bookingId="+booking.getId()+", userId="+user.getId()+", providerId="+service.getProvider().getId());
        audit.record(user,"Create Booking","Booking",booking.getId(),"User created booking");
        sendProviderNotification(booking,"You received a new booking");

        return booking;
    }

    public Page<Booking> getCurrentUserBookings(BookingStatus status, Pageable p){
        User user=current.getCurrentUser();

        if(user.getRole()==Role.PROVIDER){
            if(status==null){
                return repository.findByServiceProviderUserId(user.getId(),p);
            }
            return repository.findByServiceProviderUserIdAndStatus(user.getId(),status,p);
        }

        if(status==null){
            return repository.findByCustomerId(user.getId(),p);
        }
        return repository.findByCustomerIdAndStatus(user.getId(),status,p);
    }

    public Booking getBookingById(Long id){
        Booking booking=findBookingById(id);
        User user=current.getCurrentUser();
        boolean homeowner=booking.getCustomer().getId().equals(user.getId());
        boolean provider=booking.getService().getProvider().getUser().getId().equals(user.getId());
        if(!homeowner && !provider && user.getRole()!=Role.ADMIN){
            //Log unauthorized booking access attempts.
            logger.warning("Booking access denied: bookingId="+id+", userId="+user.getId());
            throw new UnauthorizedException("You can not view this booking");
        }
        return booking;
    }

    @Transactional
    public Booking cancelBooking(Long id){
        Booking booking=findBookingById(id);
        User user=current.getCurrentUser();
        if(!booking.getCustomer().getId().equals(user.getId()) && user.getRole()!=Role.ADMIN){
            //Log unauthorized cancellation attempts.
            logger.warning("Booking cancellation denied: bookingId="+id+", userId="+user.getId());
            throw new UnauthorizedException("Not your booking");
        }

        //Log cancellation.
        logger.info("Booking cancellation requested: bookingId="+id+", userId="+user.getId());
        logger.fine("Waiting for provider lock during cancellation: providerId="+booking.getService().getProvider().getId());
        //Lock the provider before changing a booking that occupies an appointment.
        providerLocks.lockById(booking.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        //Log successful lock acquisition.
        logger.fine("Provider lock acquired during cancellation: providerId="+booking.getService().getProvider().getId());
        entityManager.refresh(booking);
        if(booking.getStatus()==BookingStatus.COMPLETED){
            logger.warning("Booking cancellation rejected: bookingId="+id+", status="+booking.getStatus());
            throw new InvalidBookingStatusException("Completed booking can not be cancelled");
        }
        if(booking.getStatus()==BookingStatus.REJECTED || booking.getStatus()==BookingStatus.EXPIRED){
            logger.warning("Booking cancellation rejected: bookingId="+id+", status="+booking.getStatus());
            throw new InvalidBookingStatusException("Booking is no longer active");
        }
        if(booking.getStatus()==BookingStatus.CANCELLED){
            //Log an idempotent cancellation request.
            logger.info("Booking already cancelled: bookingId="+id);
            return booking;
        }

        BookingStatus oldStatus=booking.getStatus();
        booking.setLateCancellation(LocalDateTime.of(booking.getBookingDate(),booking.getStartTime()).isBefore(LocalDateTime.now().plusHours(24)));
        booking.setStatus(BookingStatus.CANCELLED);
        repository.save(booking);
        //Log the booking cancellation and whether the cancellation was late.
        logger.info("Booking cancelled: bookingId="+id+", userId="+user.getId()+", previousStatus="+oldStatus);
        sendProviderNotification(booking,"Booking cancelled");
        audit.record(user,"Cancel Booking","Booking",booking.getId(),"Booking cancelled");
        //Offer the released appointment to the next eligible waitlisted homeowner.
        if(oldStatus==BookingStatus.PENDING || oldStatus==BookingStatus.CONFIRMED || oldStatus==BookingStatus.AWAITING_REPORT){
            waitlist.offerNextAvailableProviderSlot(booking.getService().getProvider().getId(),booking.getBookingDate());
        }
        return booking;
    }

    @Transactional
    public Booking updateBookingStatus(Long id,BookingStatus newStatus){
        Booking booking=findBookingById(id);
        User user=current.getCurrentUser();

        boolean provider=booking.getService().getProvider().getUser().getId().equals(user.getId());
        if(!provider && user.getRole()!=Role.ADMIN){
            //Log unauthorized booking status update attempts.
            logger.warning("Booking status update denied: bookingId="+id+", userId="+user.getId());
            throw new UnauthorizedException("Provider or admin required");
        }

        //Log status update requests.
        logger.info("Booking status update requested: bookingId="+id+", userId="+user.getId()+", newStatus="+newStatus);
        logger.fine("Waiting for provider lock during status update: providerId="+booking.getService().getProvider().getId());
        //Lock the provider before changing the booking status.
        providerLocks.lockById(booking.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));

        //Log successful provider lock acquisition.
        logger.fine("Provider lock acquired during status update: providerId="+booking.getService().getProvider().getId());
        entityManager.refresh(booking);

        BookingStatus oldStatus=booking.getStatus();

        if(oldStatus==BookingStatus.PENDING && booking.getResponseDeadline()!=null && !LocalDateTime.now().isBefore(booking.getResponseDeadline())){
            logger.warning("Booking status update rejected: bookingId="+id+", reason=Response deadline passed");
            throw new InvalidBookingStatusException("Provider response deadline has passed");
        }

        if(newStatus==BookingStatus.COMPLETED && !reports.existsByBookingId(id)){
            logger.warning("Booking status update rejected: bookingId="+id+", reason=Required service report missing");
            throw new InvalidBookingStatusException("Submit the required service report before completing this booking");
        }

        boolean isAvailable= (oldStatus==BookingStatus.PENDING
                && (newStatus==BookingStatus.CONFIRMED
                || newStatus==BookingStatus.REJECTED
                || newStatus==BookingStatus.CANCELLED))
                || (oldStatus==BookingStatus.CONFIRMED
                && (newStatus==BookingStatus.AWAITING_REPORT
                || newStatus==BookingStatus.COMPLETED
                || newStatus==BookingStatus.CANCELLED))
                || (oldStatus==BookingStatus.AWAITING_REPORT
                && newStatus==BookingStatus.COMPLETED);

        if(!isAvailable){
            logger.warning("Invalid booking status transition: bookingId="+id+", from="+oldStatus+", to="+newStatus);
            throw new InvalidBookingStatusException("Invalid booking status transition: "+oldStatus+" -> "+newStatus);
        }

        if(newStatus==BookingStatus.AWAITING_REPORT){
            booking.setReportDeadline(LocalDateTime.of(booking.getBookingDate(),booking.getEndTime()).plusHours(24));
        }

        booking.setStatus(newStatus);
        repository.save(booking);

        //Log the saved booking status transition for auditing and troubleshooting.
        logger.info("Booking status updated: bookingId="+id+", from="+oldStatus+", to="+newStatus+", userId="+user.getId());
        audit.record(user,"Update Booking status","Booking",booking.getId(),"Booking status changed to "+newStatus);
        sendBookingNotification(booking,"Booking status changed to "+newStatus);

        //A rejected or cancelled booking releases its provider appointment.
        if(newStatus==BookingStatus.REJECTED || newStatus==BookingStatus.CANCELLED){
            waitlist.offerNextAvailableProviderSlot(booking.getService().getProvider().getId(),booking.getBookingDate());
        }
        return booking;
    }

    private void sendBookingNotification(Booking booking,String message){
        notification.sendNotifications(booking.getCustomer().getId(),message+" (#"+booking.getId()+")");
        email.sendEmail(booking.getCustomer().getEmailAddress(),"HomeHub booking update",message+" for booking #"+booking.getId());
    }

    private void sendProviderNotification(Booking booking,String message){
        User providerUser=booking.getService().getProvider().getUser();

        notification.sendNotifications(providerUser.getId(),message+" (#"+booking.getId()+")");
        email.sendEmail(providerUser.getEmailAddress(),"HomeHub new booking",message+" for booking #"+booking.getId());
    }

    private Booking findBookingById(Long id){
        return repository.findById(id).orElseThrow(()->new InformationNotFoundException("Booking not found"));
    }

    public BookingResponse toResponse(Booking booking){
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

    //Check unexpired offers for any service of the provider.
    private boolean hasOverlappingOffer(Long providerId, LocalDate date, LocalTime start, LocalTime end){
        for(BookingWaitlist offer:waiting.findByServiceProviderIdAndRequestedDateAndStatus(providerId,date, WaitlistStatus.OFFERED)){
            if(offer.getOfferExpiresAt()==null || !offer.getOfferExpiresAt().isAfter(LocalDateTime.now())){
                continue;
            }
            LocalTime offeredStart=offer.getRequestedStartTime();
            int duration=offer.getService().getDurationMinutes();
            if(duration<=0){
                continue;
            }
            LocalTime offeredEnd=offeredStart.plusMinutes(duration);
            if(start.isBefore(offeredEnd) && end.isAfter(offeredStart)){
                return true;
            }
        }
        return false;
    }
}
