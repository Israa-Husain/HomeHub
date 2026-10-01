package com.ga.HomeHub.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingRequest(Long homeId, Long serviceId, LocalDate bookingDate, LocalTime startTime, String note) {
}
