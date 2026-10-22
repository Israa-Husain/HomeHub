
package com.ga.HomeHub.service;

import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookingScheduler {
    private final BookingRepository bookings;
    private final ProviderProfileRepository providers;
    private final NotificationService notifications;
    private final BookingWaitlistService waitlist;
    private final EntityManager entityManager;

    //Automatically expire pending bookings when the provider misses the response deadline.
    @Scheduled(fixedDelay=60000)
    @Transactional
    public void expirePending(){
        LocalDateTime now=LocalDateTime.now();

        for(Booking candidate:bookings.findByStatus(BookingStatus.PENDING)){
            if(candidate.getResponseDeadline()==null || candidate.getResponseDeadline().isAfter(now)){
                continue;
            }

            //Lock the provider to prevent conflicts with concurrent booking decisions.
            providers.lockById(candidate.getService().getProvider().getId());

            //Refresh the booking after acquiring the provider lock.
            entityManager.refresh(candidate);

            //Recheck the booking status and deadline before expiring it.
            if(candidate.getStatus()==BookingStatus.PENDING && candidate.getResponseDeadline()!=null && !candidate.getResponseDeadline().isAfter(LocalDateTime.now())){
                candidate.setStatus(BookingStatus.EXPIRED);
                bookings.save(candidate);
                notifications.sendNotifications(candidate.getCustomer().getId(), "Booking #"+candidate.getId()+" expired without a provider response");

                //Offer the released appointment to the next eligible homeowner.
                waitlist.offerNextAvailableSlot(candidate.getService().getId(), candidate.getBookingDate(), candidate.getStartTime());
            }
        }
    }

    //Send appointment reminders 24 hours and 1 hour before confirmed bookings.
    @Scheduled(fixedDelay=60000)
    @Transactional
    public void reminders(){
        LocalDateTime now=LocalDateTime.now();

        for(Booking booking:bookings.findByStatus(BookingStatus.CONFIRMED)){
            LocalDateTime start=LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());

            //Send the first reminder when the appointment is within 24 hours.
            if(!booking.isReminder24Sent() && !start.isAfter(now.plusHours(24)) && start.isAfter(now.plusHours(1))){
                booking.setReminder24Sent(true);
                bookings.save(booking);
                notifyBoth(booking, "24-hour appointment reminder");
            }

            //Send the final reminder when the appointment is within 1 hour.
            if(!booking.isReminder1Sent() && !start.isAfter(now.plusHours(1)) && start.isAfter(now)){
                booking.setReminder1Sent(true);
                bookings.save(booking);
                notifyBoth(booking, "1-hour appointment reminder");
            }
        }
    }

    //Notify providers when their required service report is overdue.
    @Scheduled(fixedDelay=3600000)
    @Transactional
    public void overdueReports(){
        LocalDateTime now=LocalDateTime.now();

        for(Booking booking:bookings.findByStatus(BookingStatus.AWAITING_REPORT)){
            if(booking.getReportDeadline()!=null && !booking.getReportDeadline().isAfter(now) && !booking.isReportOverdueNotified()){
                booking.setReportOverdueNotified(true);
                bookings.save(booking);
                notifications.sendNotifications(booking.getService().getProvider().getUser().getId(), "Required service report is overdue for booking #"+booking.getId());
            }
        }
    }

    //Expire waitlist offers and offer released slots to the next homeowner.
    @Scheduled(fixedDelay=60000)
    public void expireWaitlist(){
        waitlist.expireWaitlistOffers();
    }

    //Send appointment notifications to both the homeowner and provider.
    private void notifyBoth(Booking booking, String text){
        notifications.sendNotifications(booking.getCustomer().getId(), text+" (#"+booking.getId()+")");
        notifications.sendNotifications(booking.getService().getProvider().getUser().getId(), text+" (#"+booking.getId()+")");
    }
}