package com.ga.HomeHub.dto;

import com.ga.HomeHub.model.enums.BookingStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingResponse(
        Long id,
        Long homeId,
        Long serviceId,
        String serviceName,
        String providerName,
        LocalDate bookingDate,
        LocalTime startTime,
        LocalTime endTime,
        BookingStatus status,
        String note
) {
}