package com.ga.HomeHub.booking.service;
import com.ga.HomeHub.booking.config.BookingPolicyProperties;
import com.ga.HomeHub.booking.validation.ProviderScheduleValidator;
import com.ga.HomeHub.booking.validation.ServiceEligibilityValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;


import com.ga.HomeHub.booking.dto.BookingRequest;
import com.ga.HomeHub.booking.dto.BookingResponse;
import com.ga.HomeHub.booking.exception.BookingConflictException;
import com.ga.HomeHub.booking.exception.InformationNotFoundException;
import com.ga.HomeHub.booking.exception.InvalidBookingStatusException;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.AuditLog;
import com.ga.HomeHub.booking.model.Availability;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.BookingWaitlist;
import com.ga.HomeHub.booking.model.Home;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.BookingType;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.model.enums.WaitlistStatus;
import com.ga.HomeHub.booking.repository.AvailabilityRepository;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.BookingWaitlistRepository;
import com.ga.HomeHub.booking.repository.HomeRepository;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import com.ga.HomeHub.booking.repository.ServiceOfferingRepository;
import com.ga.HomeHub.booking.repository.ServiceReportRepository;
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
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {
    //One schedule validator is shared by every provider schedule mutation.
    private final ProviderScheduleValidator schedule;
    private final BookingPolicyProperties policy;
    private final ServiceEligibilityValidator eligibility;
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

    //Lock the provider and commit the validated booking with its outbox events.
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

        //Recheck current eligibility and service terms after waiting for the existing provider lock.
        entityManager.refresh(service);
        entityManager.refresh(service.getCategory());
        entityManager.refresh(service.getProvider());
        entityManager.refresh(service.getProvider().getUser());

        //Log when the provider lock has been acquired.
        logger.fine("Provider lock acquired: providerId="+service.getProvider().getId());

        LocalDateTime validationTime=LocalDateTime.now();
        entityManager.refresh(home);
        LocalDateTime startsAt=LocalDateTime.of(request.bookingDate(),request.startTime());
        LocalTime endTime=request.startTime().plusMinutes(service.getDurationMinutes());
        BookingType type=request.bookingType()==null?BookingType.NORMAL:request.bookingType();
        if(!startsAt.isAfter(validationTime)){
            logger.warning("Booking rejected: reason=Past appointment , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Can not book a past appointment");
        }
        if(type==BookingType.NORMAL && startsAt.isBefore(validationTime.plusHours(policy.getNormalNoticeHours()))){
            logger.warning("Booking rejected: reason=Insufficient notice , providerId="+service.getProvider().getId()+", date="+request.bookingDate()+", startTime="+request.startTime());
            throw new BookingConflictException("Normal bookings require at least 24 hours notice");
        }
        if(type==BookingType.EMERGENCY && !request.bookingDate().equals(validationTime.toLocalDate())){
            throw new BookingConflictException("Emergency bookings must be on the current day");
        }
        //Emergency appointments require at least one hour of preparation time by default.
        if(type==BookingType.EMERGENCY && startsAt.isBefore(validationTime.plusHours(policy.getEmergencyNoticeHours()))){
            throw new BookingConflictException("Emergency bookings require at least "+policy.getEmergencyNoticeHours()+" hour(s) notice");
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

        if(!home.getOwner().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your home");
        }
        validateServiceEligibility(service);
        //Validate current database data only after the provider lock and entity refresh.
        schedule.requireAvailable(service.getProvider().getId(),request.bookingDate(),request.startTime(),endTime,null,null);

        Booking booking=new Booking();
        booking.setCustomer(user);
        booking.setHome(home);
        booking.setService(service);
        booking.setBookingDate(request.bookingDate());
        booking.setStartTime(request.startTime());
        booking.setEndTime(endTime);
        booking.setNote(request.note());
        booking.setBookingType(type);
        booking.setFinalPrice(BigDecimal.valueOf(service.getPrice()).multiply((type==BookingType.EMERGENCY?BigDecimal.ONE.add(BigDecimal.valueOf(policy.getEmergencySurcharge())):BigDecimal.ONE)).setScale(3,RoundingMode.HALF_UP).doubleValue());
        LocalDateTime now=LocalDateTime.now();
        booking.setResponseDeadline(now.plusHours(policy.getResponseDeadlineHours()).isBefore(startsAt)?now.plusHours(policy.getResponseDeadlineHours()):startsAt);
        booking=repository.save(booking);

        //Log the saved booking before the transaction commits.
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

    //Reuse booking participant authorization without exposing the general audit table.
    @Transactional(readOnly=true)
    public Page<AuditLog> getBookingHistory(Long id,Pageable pageable){
        getBookingById(id);
        return audit.getBookingHistory(id,pageable);
    }

    //Return one-minute start-time suggestions, not reservations. Creation still rechecks under its provider lock.
    @Transactional(readOnly=true)
    public List<LocalTime> getAvailableAppointmentTimes(Long serviceId,LocalDate date,BookingType bookingType){
        if(date==null || date.isBefore(LocalDate.now())){
            throw new IllegalArgumentException("Please select a current or future date");
        }
        ServiceOffering service=services.findById(serviceId).orElseThrow(()->new InformationNotFoundException("Service not found"));
        validateServiceEligibility(service);
        BookingType type=bookingType==null?BookingType.NORMAL:bookingType;
        //Discovery and creation enforce the same approved emergency-day restriction.
        if(type==BookingType.EMERGENCY && !date.equals(LocalDate.now())){
            throw new BookingConflictException("Emergency bookings must be on the current day");
        }
        if(type==BookingType.EMERGENCY && (!service.isEmergencyEnabled() || !service.getProvider().isEmergencyEnabled())){
            throw new BookingConflictException("Emergency booking is not enabled for this provider and service");
        }
        int duration=service.getDurationMinutes();
        if(duration<=0 || duration>=24*60){
            throw new BookingConflictException("Service must end on the same day");
        }
        Long providerId=service.getProvider().getId();
        List<Availability> windows=availability.findByProviderIdAndDateAndIsAvailableTrue(providerId,date);
        List<Availability> blocked=availability.findByProviderIdAndDateAndTimeOffTrue(providerId,date);
        List<Booking> appointments=repository.findByServiceProviderIdAndBookingDateAndStatusIn(providerId,date,
                List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT));
        List<BookingWaitlist> offers=waiting.findByServiceProviderIdAndRequestedDateAndStatus(providerId,date,WaitlistStatus.OFFERED);
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime earliest=type==BookingType.NORMAL?now.plusHours(policy.getNormalNoticeHours()):now.plusHours(policy.getEmergencyNoticeHours());
        SortedSet<LocalTime> starts=new TreeSet<>();
        for(Availability window:windows){
            int firstMinute=(window.getStartTime().toSecondOfDay()+59)/60;
            for(int minute=firstMinute;minute+duration<24*60 && (minute+duration)*60<=window.getEndTime().toSecondOfDay();minute++){
                LocalTime start=LocalTime.ofSecondOfDay(minute*60L);
                LocalTime end=start.plusMinutes(duration);
                LocalDateTime appointmentStart=LocalDateTime.of(date,start);
                if(!appointmentStart.isAfter(now) || appointmentStart.isBefore(earliest) || !window.contains(start,end)){
                    continue;
                }
                boolean timeOff=blocked.stream().anyMatch(a->start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));
                boolean booked=appointments.stream().anyMatch(b->start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));
                if(!timeOff && !booked && !schedule.hasOffer(offers,start,end,null)){
                    starts.add(start);
                }
            }
        }
        return new ArrayList<>(starts);
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
        booking.setLateCancellation(LocalDateTime.of(booking.getBookingDate(),booking.getStartTime()).isBefore(LocalDateTime.now().plusHours(policy.getCancellationNoticeHours())));
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
        //Capture decision time only after acquiring the lock and refreshing the booking.
        LocalDateTime decisionTime=LocalDateTime.now();

        if(oldStatus==BookingStatus.PENDING && booking.getResponseDeadline()!=null && !decisionTime.isBefore(booking.getResponseDeadline())){
            logger.warning("Booking status update rejected: bookingId="+id+", reason=Response deadline passed");
            throw new InvalidBookingStatusException("Provider response deadline has passed");
        }

        if(oldStatus==BookingStatus.PENDING && newStatus==BookingStatus.CONFIRMED
                && !LocalDateTime.of(booking.getBookingDate(),booking.getStartTime()).isAfter(decisionTime)){
            throw new InvalidBookingStatusException("Cannot approve a booking whose appointment has already started");
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
            if(decisionTime.isBefore(LocalDateTime.of(booking.getBookingDate(),booking.getEndTime()))){
                throw new InvalidBookingStatusException("The appointment must end before awaiting its report");
            }
            booking.setReportDeadline(LocalDateTime.of(booking.getBookingDate(),booking.getEndTime()).plusHours(policy.getReportDeadlineHours()));
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
                booking.getBookingType()==null ? null : booking.getBookingType().name(),
                booking.getFinalPrice(),
                booking.getResponseDeadline(),
                booking.getService().getProvider().getId(),
                booking.getReportDeadline(),
                booking.isLateCancellation(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }

    //Keep discovery and locked creation aligned without duplicating eligibility rules.
    private void validateServiceEligibility(ServiceOffering service){
        eligibility.require(service);
    }

}