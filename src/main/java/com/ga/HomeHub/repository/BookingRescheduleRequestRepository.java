package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.BookingRescheduleRequest;
import com.ga.HomeHub.model.enums.RescheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRescheduleRequestRepository extends JpaRepository<BookingRescheduleRequest,Long> {
    boolean existsByBookingIdAndStatus(Long bookingId, RescheduleStatus status);

    //Scope the query before pagination; one query supports both participant roles and optional filters.
    @Query("SELECT r FROM BookingRescheduleRequest r WHERE " +
            "((:providerRole = true AND r.booking.service.provider.user.id = :userId) OR " +
            "(:providerRole = false AND r.booking.customer.id = :userId)) AND " +
            "(:status IS NULL OR r.status = :status) AND (:bookingId IS NULL OR r.booking.id = :bookingId)")
    Page<BookingRescheduleRequest> findCurrentUserRequests(@Param("userId") Long userId,@Param("providerRole") boolean providerRole,
            @Param("status") RescheduleStatus status,@Param("bookingId") Long bookingId,Pageable pageable);

}
