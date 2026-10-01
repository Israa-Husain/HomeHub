package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.enums.ServiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {
    Page<ServiceOffering> findByCategoryIdAndStatus(Long categoryId, ServiceStatus status, Pageable pageable);
    Page<ServiceOffering> findByStatus(ServiceStatus status, Pageable pageable);
}
