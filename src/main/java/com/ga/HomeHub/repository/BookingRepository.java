package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking,Long> {
    Page<Booking> findByCustomerId(Long customerId, Pageable pageable);
    Page<Booking> findByCustomerIdAndStatus(Long customerId, BookingStatus status, Pageable pageable);
    List<Booking> findServiceProviderIdAndBookingDateAndStatusIn(Long providerId, LocalDate bookingDate, Collection<BookingStatus> statuses);
}
