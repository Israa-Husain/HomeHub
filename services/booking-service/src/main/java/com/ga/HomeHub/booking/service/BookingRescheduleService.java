package com.ga.HomeHub.booking.service;
import com.ga.HomeHub.booking.config.BookingPolicyProperties;
import com.ga.HomeHub.booking.validation.ProviderScheduleValidator;
import com.ga.HomeHub.booking.validation.ServiceEligibilityValidator;


import com.ga.HomeHub.booking.exception.BookingConflictException;
import com.ga.HomeHub.booking.exception.InformationNotFoundException;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.BookingRescheduleRequest;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.RescheduleStatus;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.BookingRescheduleRequestRepository;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingRescheduleService {
    //One schedule validator is shared by every provider schedule mutation.
    private final ProviderScheduleValidator schedule;
    private final BookingPolicyProperties policy;
    private final ServiceEligibilityValidator eligibility;
    private final BookingRescheduleRequestRepository requests;
    private final BookingRepository bookings;
    private final ProviderProfileRepository providers;
    private final CurrentUserService current;
    private final AuditLogService audit;
    private final BookingWaitlistService waitlist;
    private final EntityManager entityManager;
    private final NotificationService notifications;
    private static final Logger logger = Logger.getLogger(BookingRescheduleService.class.getName());

    //List only the authenticated participant's requests, including decisions retained in the database.
    @Transactional(readOnly=true)
    public Page<BookingRescheduleRequest> getCurrentUserRequests(RescheduleStatus status,Long bookingId,Pageable pageable){
        User user=current.getCurrentUser();
        if(user.getRole()!=Role.HOMEOWNER && user.getRole()!=Role.PROVIDER){
            throw new UnauthorizedException("Homeowner or provider required");
        }
        return requests.findCurrentUserRequests(user.getId(),user.getRole()==Role.PROVIDER,status,bookingId,pageable);
    }

    @Transactional(readOnly=true)
    public BookingRescheduleRequest getRequestById(Long id){
        BookingRescheduleRequest request=requests.findById(id).orElseThrow(()->new InformationNotFoundException("Reschedule request not found"));
        User user=current.getCurrentUser();
        Booking booking=request.getBooking();
        boolean homeowner=user.getRole()==Role.HOMEOWNER && booking.getCustomer().getId().equals(user.getId());
        boolean provider=user.getRole()==Role.PROVIDER && booking.getService().getProvider().getUser().getId().equals(user.getId());
        if(!homeowner && !provider){
            throw new UnauthorizedException("You can not view this reschedule request");
        }
        return request;
    }


    //Allows a homeowner to request a different appointment without changing the original booking until the provider approves.
    @Transactional
    public BookingRescheduleRequest request(Long bookingId, LocalDate date, LocalTime start){
        Booking booking=bookings.findById(bookingId).orElseThrow(()->new InformationNotFoundException("Booking not found"));
        User user=current.getCurrentUser();

        logger.info("Reschedule requested: bookingId="+bookingId+", userId="+user.getId()+", date="+date+", start="+start);
        if(!booking.getCustomer().getId().equals(user.getId())){
            logger.warning("Reschedule request denied: unauthorized userId="+user.getId()+", bookingId="+bookingId);
            throw new UnauthorizedException("Not your booking");
        }
        //Serialize duplicate-pending checks with provider decisions and other booking mutations.
        Long providerId=booking.getService().getProvider().getId();
        logger.fine("Waiting for provider lock: providerId="+providerId+", bookingId="+bookingId);
        providers.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        entityManager.refresh(booking);
        if(booking.getStatus()!=BookingStatus.PENDING && booking.getStatus()!=BookingStatus.CONFIRMED){
            logger.warning("Reschedule rejected: bookingId="+bookingId+", bookingStatus="+booking.getStatus());
            throw new BookingConflictException("Booking can not be rescheduled");
        }
        //Rescheduling requires notice before the original appointment as well as the replacement.
        if(LocalDateTime.of(booking.getBookingDate(),booking.getStartTime()).isBefore(LocalDateTime.now().plusHours(policy.getRescheduleNoticeHours()))){
            throw new BookingConflictException("The original appointment is too close to reschedule");
        }
        if(requests.existsByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING)){
            logger.warning("Reschedule rejected: pending request already exists for bookingId="+bookingId);
            throw new BookingConflictException("An active request already exists");
        }

        //Require at least 24 hours notice for the new appointment.
        if(date==null || start==null || LocalDateTime.of(date, start).isBefore(LocalDateTime.now().plusHours(policy.getRescheduleNoticeHours()))){
            logger.warning("Reschedule rejected: insufficient notice for bookingId="+bookingId);
            throw new BookingConflictException("Rescheduling requires 24 hours notice");
        }

        BookingRescheduleRequest request=new BookingRescheduleRequest();
        request.setBooking(booking);
        request.setRequester(user);
        request.setRequestedDate(date);
        request.setRequestedStartTime(start);
        request=requests.save(request);
        audit.record(user, "Request Reschedule", "Booking", bookingId, "Requested "+date+" "+start);

        logger.info("Reschedule request saved: requestId="+request.getId()+", bookingId="+bookingId);
        notifications.sendNotifications(booking.getService().getProvider().getUser().getId(),
                "Reschedule request #"+request.getId()+" for booking #"+bookingId+": "+date+" at "+start);
        return request;
    }

    //Allows the assigned provider to approve or reject a request. Approval rechecks the appointment while holding the provider database lock.
    @Transactional
    public BookingRescheduleRequest decide(Long id, boolean approve){
        BookingRescheduleRequest request=requests.findById(id).orElseThrow(()->new InformationNotFoundException("Reschedule request not found"));
        Booking b=request.getBooking();
        User user=current.getCurrentUser();
        //Log the provider decision before acquiring the provider database lock.
        logger.info("Reschedule decision requested: requestId="+id+", bookingId="+b.getId()+", approve="+approve+", userId="+user.getId());
        if(!b.getService().getProvider().getUser().getId().equals(user.getId())){
            throw new UnauthorizedException("Not assigned provider");
        }


        Long lockedProviderId=b.getService().getProvider().getId();
        logger.fine("Waiting for provider lock: providerId="+lockedProviderId+", requestId="+id);
        //Lock the provider before validating or changing the booking to prevent concurrent bookings and rescheduling conflicts.
        providers.lockById(b.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        logger.fine("Provider lock acquired: providerId="+lockedProviderId+", requestId="+id);
        entityManager.refresh(request);
        entityManager.refresh(b);
        if(request.getStatus()!=RescheduleStatus.PENDING){
            logger.warning("Reschedule decision rejected: requestId="+id+", status="+request.getStatus());
            throw new BookingConflictException("Request already decided");
        }

        //A booking may have changed status while the request was pending.
        if(b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.CONFIRMED){
            logger.warning("Reschedule decision rejected: bookingId="+b.getId()+", status="+b.getStatus());
            throw new BookingConflictException("Booking can no longer be rescheduled");
        }
        if(approve){
            if(LocalDateTime.of(b.getBookingDate(),b.getStartTime()).isBefore(LocalDateTime.now().plusHours(policy.getRescheduleNoticeHours()))){
                throw new BookingConflictException("The original appointment is too close to reschedule");
            }
            eligibility.refreshAndRequire(b.getService());
            LocalDate date=request.getRequestedDate();
            LocalTime start=request.getRequestedStartTime();

            //Recheck the 24-hour notice rule at approval time.
            if(date==null || start==null || LocalDateTime.of(date, start).isBefore(LocalDateTime.now().plusHours(policy.getRescheduleNoticeHours()))){
                logger.warning("Reschedule approval rejected: insufficient notice, requestId="+id);
                throw new BookingConflictException("Rescheduling requires 24 hours notice");
            }

            int duration=b.getService().getDurationMinutes();

            //Reject appointments that would end after midnight because HomeHub stores appointment start and end on the same date.
            if(duration<=0 || start.toSecondOfDay()+(long)duration*60>=24*60*60){
                logger.warning("Reschedule approval rejected: invalid duration/time, requestId="+id);
                throw new BookingConflictException("Invalid rescheduled appointment time");
            }

            LocalTime end=start.plusMinutes(duration);
            Long providerId=b.getService().getProvider().getId();
            //Exclude the existing booking, but respect every outstanding waitlist offer.
            schedule.requireAvailable(providerId,date,start,end,b.getId(),null);

            //Only update the original booking after the provider approves and all availability and concurrency checks succeed.
            LocalDate oldDate=b.getBookingDate();
            b.setBookingDate(date);
            b.setStartTime(start);
            b.setEndTime(end);
            //Reset reminders when an appointment is rescheduled so the homeowner and provider receive reminders for the new date.
            b.setReminder24Sent(false);
            b.setReminder1Sent(false);
            //A pending booking gets a response deadline for the approved replacement appointment.
            if(b.getStatus()==BookingStatus.PENDING){
                LocalDateTime deadline=LocalDateTime.now().plusHours(policy.getResponseDeadlineHours());
                LocalDateTime appointment=LocalDateTime.of(date,start);
                b.setResponseDeadline(deadline.isBefore(appointment)?deadline:appointment);
            }
            bookings.save(b);

            logger.info("Reschedule booking updated: bookingId="+b.getId()+", oldDate="+oldDate+", newDate="+date+", newStart="+start);
            waitlist.offerNextAvailableProviderSlot(providerId,oldDate);
        }

        request.setStatus(approve?RescheduleStatus.APPROVED:RescheduleStatus.REJECTED);
        audit.record(user, "Reschedule Decision", "Booking", b.getId(), approve?"Approved":"Rejected");
        //Log the provider decision before the transaction commits.
        request=requests.save(request);
        logger.info("Reschedule decision saved: requestId="+id+", bookingId="+b.getId()+", status="+request.getStatus());
        notifications.sendNotifications(b.getCustomer().getId(),
                "Reschedule request #"+id+" for booking #"+b.getId()+" was "+request.getStatus().name().toLowerCase());
        return request;
    }
}