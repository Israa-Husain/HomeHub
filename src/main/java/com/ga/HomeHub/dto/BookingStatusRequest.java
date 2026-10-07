package com.ga.HomeHub.dto;

import com.ga.HomeHub.model.enums.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record BookingStatusRequest(@NotNull BookingStatus status) {
}
