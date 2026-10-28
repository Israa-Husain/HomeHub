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
    //Include inactive services only when listing the authenticated provider's own offerings.
    Page<ServiceOffering> findByProviderId(Long providerId, Pageable pageable);

    @Query("SELECT s FROM ServiceOffering s WHERE " +
            "s.status = :status AND " +
            "s.category.status = com.ga.HomeHub.model.enums.CategoryStatus.ACTIVE AND " +
            "s.provider.providerStatus = com.ga.HomeHub.model.enums.ProviderStatus.APPROVED AND " +
            "s.provider.user.status = com.ga.HomeHub.model.enums.UserStatus.ACTIVE AND " +
            "(:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%'))) AND " +
            "(:categoryId IS NULL OR s.category.id = :categoryId) AND " +
            "(:providerId IS NULL OR s.provider.id = :providerId) AND " +
            "(:minPrice IS NULL OR s.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR s.price <= :maxPrice)")
    Page<ServiceOffering> searchServices(
            @Param("name") String name,
            @Param("categoryId") Long categoryId,
            @Param("providerId") Long providerId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("status") ServiceStatus status,
            Pageable pageable
    );
}
