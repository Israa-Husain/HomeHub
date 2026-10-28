package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.enums.ProviderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, Long> {
    Optional<ProviderProfile> findByUserId(Long userId);
    Page<ProviderProfile> findByProviderStatus(ProviderStatus providerStatus,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProviderProfile p WHERE p.id=:id")
    Optional<ProviderProfile> lockById(@Param("id") Long id);
}
