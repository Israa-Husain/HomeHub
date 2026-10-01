package com.ga.HomeHub.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityRequest(LocalDate date, LocalTime startTime, LocalTime endTime) {
}
