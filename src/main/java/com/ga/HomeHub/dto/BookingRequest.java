package com.ga.HomeHub.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingRequest(@NotNull Long homeId, @NotNull Long serviceId, @NotNull LocalDate bookingDate, @NotNull LocalTime startTime, String note) {
}
