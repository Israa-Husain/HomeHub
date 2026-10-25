package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.AvailabilityRequest;
import com.ga.HomeHub.model.Availability;
import com.ga.HomeHub.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name="Availability", description="HomeHub availability API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/availability")
public class AvailabilityController {
    private final AvailabilityService availabilityService;

    @Operation(summary="Get provider availability by date")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping
    public List<Availability> getProviderAvailabilityByDate(@RequestParam Long providerId, @RequestParam LocalDate date){
        return availabilityService.getProviderAvailabilityByDate(providerId, date);
    }

    @Operation(summary="Create availability")
    @ApiResponses(value={
            @ApiResponse(responseCode="201",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Availability> createAvailability(@Valid @RequestBody AvailabilityRequest request){
        return ResponseEntity.status(201).body(availabilityService.createAvailability(request));
    }

    @Operation(summary="Delete availability")
    @ApiResponses(value={
            @ApiResponse(responseCode="204",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @DeleteMapping("/{availabilityId}")
    @PreAuthorize("hasRole('PROVIDER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvailability(@PathVariable Long availabilityId){
        availabilityService.deleteAvailability(availabilityId);
    }

    @Operation(summary="Block time off")
    @ApiResponses(value={
            @ApiResponse(responseCode="201",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping("/time-off")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Availability> blockTimeOff(@Valid @RequestBody AvailabilityRequest request){
        return ResponseEntity.status(201).body(availabilityService.blockTimeOff(request));
    }
}
