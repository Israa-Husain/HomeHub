package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.ServiceReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceReportRepository extends JpaRepository<ServiceReport,Long> {
    boolean existsByBookingId(Long bookingId);
    Optional<ServiceReport> findBookingId(Long bookingId);
}
