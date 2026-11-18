package com.ga.HomeHub.booking.service;

import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class BookingScheduler {
    private static final Logger log = LoggerFactory.getLogger(BookingScheduler.class);
    private final BookingRepository bookings;
    private final ProviderProfileRepository providers;
    private final NotificationService notifications;
    private final BookingWaitlistService waitlist;
    private final AuditLogService audit;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    public void expirePending() {
        process(BookingStatus.PENDING, booking -> {
            if (booking.getResponseDeadline() == null || booking.getResponseDeadline().isAfter(LocalDateTime.now())) {
                return;
            }
            booking.setStatus(BookingStatus.EXPIRED);
            audit.record(null, "Expire Booking", "Booking", booking.getId(), "System expired the provider response deadline");
            notifications.sendNotifications(booking.getCustomer().getId(), "Booking #" + booking.getId() + " expired without a provider response");
            waitlist.offerNextAvailableProviderSlot(booking.getService().getProvider().getId(), booking.getBookingDate());
        });
    }

    public void reminders() {
        process(BookingStatus.CONFIRMED, booking -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
            if (!booking.isReminder24Sent() && !start.isAfter(now.plusHours(24)) && start.isAfter(now.plusHours(1))) {
                booking.setReminder24Sent(true);
                notifyBoth(booking, "24-hour appointment reminder");
            }
            if (!booking.isReminder1Sent() && !start.isAfter(now.plusHours(1)) && start.isAfter(now)) {
                booking.setReminder1Sent(true);
                notifyBoth(booking, "1-hour appointment reminder");
            }
        });
    }

    public void overdueReports() {
        process(BookingStatus.AWAITING_REPORT, booking -> {
            if (booking.getReportDeadline() != null && !booking.getReportDeadline().isAfter(LocalDateTime.now()) && !booking.isReportOverdueNotified()) {
                booking.setReportOverdueNotified(true);
                notifications.sendNotifications(booking.getService().getProvider().getUser().getId(), "Required service report is overdue for booking #" + booking.getId());
            }
        });
    }

    public void expireWaitlist() {
        waitlist.expireWaitlistOffers();
    }

    //Process each candidate in a new transaction and acquire its provider lock.
    private void process(BookingStatus expected, Consumer<Booking> action) {
        List<Long> ids = bookings.findByStatus(expected).stream().map(Booking::getId).toList();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        for (Long id : ids) {
            try {
                transaction.execute(status -> {
                    Booking booking = bookings.findById(id).orElse(null);
                    if (booking == null) {
                        return null;
                    }
                    providers.lockById(booking.getService().getProvider().getId()).orElseThrow();
                    entityManager.refresh(booking);
                    if (booking.getStatus() != expected) {
                        return null;
                    }
                    action.accept(booking);
                    bookings.save(booking);
                    return null;
                });
            } catch (Exception e) {
                log.error("Scheduled booking candidate failed bookingId={}", id, e);
            }
        }
    }

    private void notifyBoth(Booking booking, String message) {
        notifications.sendNotifications(booking.getCustomer().getId(), message + " (#" + booking.getId() + ")");
        notifications.sendNotifications(booking.getService().getProvider().getUser().getId(), message + " (#" + booking.getId() + ")");
    }
}