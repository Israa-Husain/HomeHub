package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.dto.BookingResponse;
import com.ga.HomeHub.dto.BookingStatusRequest;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.service.BookingService;
import jakarta.validation.Valid;
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
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request){
        Booking booking = bookingService.createBooking(request);
        return ResponseEntity.status(201).body(bookingService.toResponse(booking));
    }

    @GetMapping
    public Page<BookingResponse> getCurrentUserBookings(@RequestParam(required = false)BookingStatus status, Pageable pageable){
        return bookingService.getCurrentUserBookings(status, pageable).map(bookingService::toResponse);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBookingById(@PathVariable Long bookingId) {
        return bookingService.toResponse(bookingService.getBookingById(bookingId));
    }

    @DeleteMapping("/{bookingId}")
    public BookingResponse cancelBooking(@PathVariable Long bookingId){
        return bookingService.toResponse(bookingService.cancelBooking(bookingId));
    }

    @PutMapping("/{bookingId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','PROVIDER')")
    public BookingResponse updateBookingStatus(@PathVariable Long bookingId, @Valid @RequestBody BookingStatusRequest request){
        return bookingService.toResponse(bookingService.updateBookingStatus(bookingId, request.status()));
    }
}
