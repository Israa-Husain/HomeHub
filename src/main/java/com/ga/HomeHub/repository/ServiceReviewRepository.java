package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.ServiceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ServiceReviewRepository extends JpaRepository<ServiceReview,Long> {
    boolean existsByBookingId(Long bookingId);
    List<ServiceReview> findByBookingServiceProviderId(Long providerId);
    Page<ServiceReview> findByHomeownerId(Long homeownerId,Pageable pageable);
}
