package com.ga.HomeHub.service;

import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
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

            Booking b=bookings.findById(candidate.getId()).orElse(null);

            //Recheck the booking status and deadline before expiring it.
            if(b!=null && b.getStatus()==BookingStatus.PENDING && b.getResponseDeadline()!=null && !b.getResponseDeadline().isAfter(LocalDateTime.now())){
                b.setStatus(BookingStatus.EXPIRED);
                bookings.save(b);

                notifications.sendNotifications(b.getCustomer().getId(), "Booking #"+b.getId()+" expired without a provider response");

                //Offer the released appointment to the next eligible homeowner.
                waitlist.offerNext(b.getService().getId(), b.getBookingDate(), b.getStartTime());
            }
        }
    }

    //Send appointment reminders 24 hours and 1 hour before confirmed bookings.
    @Scheduled(fixedDelay=60000)
    @Transactional
    public void reminders(){
        LocalDateTime now=LocalDateTime.now();

        for(Booking b:bookings.findByStatus(BookingStatus.CONFIRMED)){
            LocalDateTime start=LocalDateTime.of(b.getBookingDate(), b.getStartTime());

            //Send the first reminder when the appointment is within 24 hours.
            if(!b.isReminder24Sent() && !start.isAfter(now.plusHours(24)) && start.isAfter(now.plusHours(1))){
                b.setReminder24Sent(true);
                bookings.save(b);
                notifyBoth(b, "24-hour appointment reminder");
            }

            //Send the final reminder when the appointment is within 1 hour.
            if(!b.isReminder1Sent() && !start.isAfter(now.plusHours(1)) && start.isAfter(now)){
                b.setReminder1Sent(true);
                bookings.save(b);
                notifyBoth(b, "1-hour appointment reminder");
            }
        }
    }

    //Notify providers when their required service report is overdue.
    @Scheduled(fixedDelay=3600000)
    @Transactional
    public void overdueReports(){
        LocalDateTime now=LocalDateTime.now();
        for(Booking b:bookings.findByStatus(BookingStatus.AWAITING_REPORT)){
            if(b.getReportDeadline()!=null && !b.getReportDeadline().isAfter(now) && !b.isReportOverdueNotified()){
                b.setReportOverdueNotified(true);
                bookings.save(b);
                notifications.sendNotifications(b.getService().getProvider().getUser().getId(), "Required service report is overdue for booking #"+b.getId());
            }
        }
    }

    //Expire waitlist offers and offer released slots to the next homeowner.
    @Scheduled(fixedDelay=60000)
    public void expireWaitlist(){
        waitlist.expireOffers();
    }

    //Send appointment notifications to both the homeowner and provider.
    private void notifyBoth(Booking b, String text){
        notifications.sendNotifications(b.getCustomer().getId(), text+" (#"+b.getId()+")");
        notifications.sendNotifications(b.getService().getProvider().getUser().getId(), text+" (#"+b.getId()+")");
    }
}