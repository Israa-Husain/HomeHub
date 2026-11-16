package com.ga.HomeHub.booking.dto;

import com.ga.HomeHub.booking.model.enums.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record BookingStatusRequest(@NotNull BookingStatus status) {
}
