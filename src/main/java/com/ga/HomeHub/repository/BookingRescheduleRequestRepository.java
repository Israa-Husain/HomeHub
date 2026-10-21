package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.BookingRescheduleRequest;
import com.ga.HomeHub.model.enums.RescheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRescheduleRequestRepository extends JpaRepository<BookingRescheduleRequest,Long> {
    boolean existsByBookingIdAndStatus(Long bookingId, RescheduleStatus status);

}
