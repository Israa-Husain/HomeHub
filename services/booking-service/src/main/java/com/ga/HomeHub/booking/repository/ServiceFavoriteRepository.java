package com.ga.HomeHub.booking.repository;

import com.ga.HomeHub.booking.model.ServiceFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceFavoriteRepository extends JpaRepository<ServiceFavorite,Long> {
    boolean existsByHomeownerIdAndServiceId(Long homeownerId,Long serviceId);
    List<ServiceFavorite> findByHomeownerId(Long homeownerId);
    Optional<ServiceFavorite> findByIdAndHomeownerId(Long id, Long homeownerId);
}
