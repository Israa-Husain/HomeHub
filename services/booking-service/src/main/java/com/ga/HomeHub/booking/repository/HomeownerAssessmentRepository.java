package com.ga.HomeHub.booking.repository;

import com.ga.HomeHub.booking.model.HomeownerAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomeownerAssessmentRepository extends JpaRepository<HomeownerAssessment,Long> {
    boolean existsByBookingId(Long bookingId);
    List<HomeownerAssessment> findByHomeownerId(Long homeownerId);
}
