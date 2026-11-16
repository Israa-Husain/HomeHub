package com.ga.HomeHub.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record HomeRequest(@NotBlank String name, @NotBlank String city, @NotBlank String address) {
}
