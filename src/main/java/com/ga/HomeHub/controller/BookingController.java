package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.service.BookingService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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


}
