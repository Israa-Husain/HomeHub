package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.BookingWaitlist;
import com.ga.HomeHub.model.enums.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface BookingWaitlistRepository extends JpaRepository<BookingWaitlist,Long> {
    List<BookingWaitlist> findByServiceIdAndRequestedDateAndRequestedStartTimeAndStatusOrderByCreatedAtAsc(Long serviceId, LocalDate requestedDate, LocalTime requestedStartTime, WaitlistStatus status);
    List<BookingWaitlist> findByStatusAndOfferExpiresAtBefore(WaitlistStatus status, LocalDateTime time);
    List<BookingWaitlist> findByHomeownerId(Long homeownerId);
    boolean existsByServiceIdAndRequestedDateAndRequestedStartTimeAndStatus(Long serviceId, LocalDate requestedDate, LocalTime requestedStartTime, WaitlistStatus status);
    List<BookingWaitlist> findByServiceProviderIdAndRequestedDateAndStatus(Long providerId, LocalDate requestedDate, WaitlistStatus status);

}
