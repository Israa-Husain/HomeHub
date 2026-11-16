package com.ga.HomeHub.notification.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class EventReceiptRepository {
    private final JdbcTemplate jdbc;

    public EventReceiptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    //Use published outbox receipt markers and the unique event key to coordinate duplicate deliveries.
    public boolean claim(String eventId, String fingerprint) {
        String id = "notification-receipt:v1:" + eventId;
        LocalDateTime now = LocalDateTime.now();

        int inserted = jdbc.update(
                "insert into outbox_event(id,topic,event_key,payload,created_at,published_at,attempts) "
                        + "values (?,?,?,?,?,?,0) on conflict do nothing",
                id,
                "homehub.notification.receipts.v1",
                eventId,
                fingerprint,
                now,
                now
        );

        if (inserted == 1) {
            return true;
        }

        String existing = jdbc.queryForObject(
                "select payload from outbox_event where id=?",
                String.class,
                id
        );

        if (!fingerprint.equals(existing)) {
            throw new IllegalArgumentException("Event ID was reused with different content");
        }

        return false;
    }
}