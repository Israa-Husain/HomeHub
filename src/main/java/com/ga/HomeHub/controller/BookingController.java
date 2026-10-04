package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.dto.BookingStatusRequest;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.service.BookingService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/booking")
public class BookingController {
    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('HOMEOWNER')")
    public ResponseEntity<Booking> createBooking(@RequestBody BookingRequest request){
        return ResponseEntity.status(201).body(bookingService.createBooking(request));
    }

    @GetMapping
    public Page<Booking> getCurrentUserBookings(@RequestParam(required = false)BookingStatus status, Pageable pageable){
        return bookingService.getCurrentUserBookings(status, pageable);
    }

    @DeleteMapping("/{bookingId}")
    public Booking cancelBooking(@PathVariable Long bookingId){
        return bookingService.cancelBooking(bookingId);
    }

    @PutMapping("/{bookingId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','PROVIDER')")
    public Booking updateBookingStatus(@PathVariable Long bookingId, @RequestBody BookingStatusRequest request){
        return bookingService.updateBookingStatus(bookingId,request.status());
    }
}
