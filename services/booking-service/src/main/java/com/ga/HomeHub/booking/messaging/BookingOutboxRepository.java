package com.ga.HomeHub.booking.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface BookingOutboxRepository extends JpaRepository<BookingOutboxEvent,String>{
    //Each publisher owns only rows it can lock in its current transaction.
    @Query(value="SELECT * FROM outbox_event WHERE published_at is null ORDER BY created_at,id LIMIT 50 for update skip locked",nativeQuery=true)
    List<BookingOutboxEvent> lockPending();
}