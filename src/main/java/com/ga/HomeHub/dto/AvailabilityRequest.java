package com.ga.HomeHub.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityRequest(@NotNull LocalDate date, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {
}
