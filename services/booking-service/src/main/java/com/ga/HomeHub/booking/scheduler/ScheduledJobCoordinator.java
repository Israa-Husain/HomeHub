package com.ga.HomeHub.booking.scheduler;

import com.ga.HomeHub.booking.service.BookingScheduler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@RequiredArgsConstructor
public class ScheduledJobCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ScheduledJobCoordinator.class);
    private final DataSource dataSource;
    private final BookingScheduler worker;

    @Scheduled(fixedDelayString = "${homehub.jobs.expiry-ms:60000}")
    public void expire() {
        run(70448422001L, worker::expirePending);
    }

    @Scheduled(fixedDelayString = "${homehub.jobs.reminder-ms:60000}")
    public void remind() {
        run(70448422002L, worker::reminders);
    }

    @Scheduled(fixedDelayString = "${homehub.jobs.report-ms:3600000}")
    public void reports() {
        run(70448422003L, worker::overdueReports);
    }

    @Scheduled(fixedDelayString = "${homehub.jobs.waitlist-ms:60000}")
    public void waitlist() {
        run(70448422004L, worker::expireWaitlist);
    }

    //Separate session lock survives worker commits and is released even when a worker fails.
    public void run(long key, Runnable task) {
        try (Connection connection = dataSource.getConnection()) {
            boolean acquired;
            try (var statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
                statement.setLong(1, key);
                try (var result = statement.executeQuery()) {
                    result.next();
                    acquired = result.getBoolean(1);
                }
            }
            if (!acquired) {
                return;
            }
            try {
                task.run();
            } finally {
                try (var statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
                    statement.setLong(1, key);
                    statement.execute();
                }
            }
        } catch (Exception e) {
            log.error("Scheduled job failed key={}", key, e);
        }
    }
}