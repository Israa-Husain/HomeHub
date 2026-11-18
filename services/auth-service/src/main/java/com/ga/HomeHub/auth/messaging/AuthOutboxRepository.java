package com.ga.HomeHub.auth.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

//Skip locked rows so separate publisher instances process different batches.
public interface AuthOutboxRepository extends JpaRepository<AuthOutboxEvent,String>{

    @Query(value="SELECT * FROM outbox_event WHERE published_at IS NULL ORDER BY created_at,id LIMIT 50 FOR UPDATE SKIP LOCKED",nativeQuery=true)
    List<AuthOutboxEvent> lockPending();
}