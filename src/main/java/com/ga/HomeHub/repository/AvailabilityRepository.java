package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.Availability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    List<Availability> findByProviderIdAndDate(Long providerId, LocalDate date);
}
