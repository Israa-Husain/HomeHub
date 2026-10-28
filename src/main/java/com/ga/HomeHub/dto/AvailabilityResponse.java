package com.ga.HomeHub.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

//Expose scheduling fields without serializing the provider's linked user account.
public record AvailabilityResponse(
        Long id,
        Long providerId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean available,
        boolean timeOff,
        LocalDateTime createdAt,
        LocalDateTime updatedAt){
}
