package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.HomeownerAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomeownerAssessmentRepository extends JpaRepository<HomeownerAssessment,Long> {
    boolean existsByBookingId(Long bookingId);
    List<HomeownerAssessment> findByHomeownerId(Long homeownerId);
}
