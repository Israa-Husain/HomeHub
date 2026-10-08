package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.enums.ServiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {
    Page<ServiceOffering> findByCategoryIdAndStatus(Long categoryId, ServiceStatus status, Pageable pageable);
    Page<ServiceOffering> findByStatus(ServiceStatus status, Pageable pageable);

    @Query("SELECT s FROM ServiceOffering s WHERE " +
            "s.status = :status AND " +
            "(:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:categoryId IS NULL OR s.category.id = :categoryId) AND " +
            "(:minPrice IS NULL OR s.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR s.price <= :maxPrice)")
    Page<ServiceOffering> searchServices(
            @Param("name") String name,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("status") ServiceStatus status,
            Pageable pageable
    );
}
