package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.AvailabilityRequest;
import com.ga.HomeHub.model.Availability;
import com.ga.HomeHub.service.AvailabilityService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/availability")
public class AvailabilityController {
    private final AvailabilityService availabilityService;

    @GetMapping
    public List<Availability> getProviderAvailabilityByDate(@RequestParam Long providerId, @RequestParam LocalDate date){
        return availabilityService.getProviderAvailabilityByDate(providerId, date);
    }

    @PostMapping
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Availability> createAvailability(@RequestBody AvailabilityRequest request){
        return ResponseEntity.status(201).body(availabilityService.createAvailability(request));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('PROVIDER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvailability(@PathVariable Long availabilityId){
        availabilityService.deleteAvailability(availabilityId);
    }
}
