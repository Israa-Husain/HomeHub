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


    //Transactional provider locking prevents expiry from racing approval.
    @Scheduled(fixedDelay=60000)
    @Transactional
    public void expirePending(){
        for(Booking candidate:bookings.findByStatus(BookingStatus.PENDING)){
            if(candidate.getResponseDeadline()==null || candidate.getResponseDeadline().isAfter(LocalDateTime.now())){
                continue;
            }
            providers.lockById(candidate.getService().getProvider().getId());
            Booking b=bookings.findById(candidate.getId()).orElse(null);
            if(b!=null && b.getStatus()==BookingStatus.PENDING && !b.getResponseDeadline().isAfter(LocalDateTime.now())){
                b.setStatus(BookingStatus.EXPIRED);bookings.save(b);
                notifications.sendNotifications(b.getCustomer().getId(),"Booking #"+b.getId()+" expired without a provider response");
                waitlist.offerNext(b.getService().getId(),b.getBookingDate(),b.getStartTime());
            }
        }
    }

    //Persist reminder flags so repeated scheduler runs do not duplicate messages.
    @Scheduled(fixedDelay=60000)
    @Transactional
    public void reminders(){
        LocalDateTime now= LocalDateTime.now();
        for(Booking b:bookings.findByStatus(BookingStatus.CONFIRMED)){
            LocalDateTime start=LocalDateTime.of(b.getBookingDate(),b.getStartTime());
            if(!b.isReminder24Sent() && !start.isAfter(now.plusHours(24)) && start.isAfter(now.plusHours(1))){
                b.setReminder24Sent(true);notifyBoth(b,"24-hour appointment reminder");
            }
            if(!b.isReminder1Sent() && !start.isAfter(now.plusHours(1)) && start.isAfter(now)){
                b.setReminder1Sent(true);notifyBoth(b,"1-hour appointment reminder");
            }
        }
    }

    //Keep report requirement active and notify providers when overdue.
    @Scheduled(fixedDelay=3600000)
    @Transactional
    public void overdueReports(){
        for(Booking b:bookings.findByStatus(BookingStatus.AWAITING_REPORT)){
            if(b.getReportDeadline()!=null && b.getReportDeadline().isBefore(LocalDateTime.now()) && !b.isReportOverdueNotified()){
                b.setReportOverdueNotified(true);
                notifications.sendNotifications(b.getService().getProvider().getUser().getId(),"Required service report is overdue for booking #"+b.getId());
            }
        }
    }

    //Reassign offers that have passed their claim deadline.
    @Scheduled(fixedDelay=60000)
    public void expireWaitlist(){waitlist.expireOffers();}
    private void notifyBoth(Booking b,String text){
        notifications.sendNotifications(b.getCustomer().getId(),text+" (#"+b.getId()+")");
        notifications.sendNotifications(b.getService().getProvider().getUser().getId(),text+" (#"+b.getId()+")");
    }
}