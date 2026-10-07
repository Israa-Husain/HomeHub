package com.ga.HomeHub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ServiceRequest(@NotNull Long categoryId, @NotBlank String name, String description, @NotNull @Positive Double price, @NotNull @Positive Integer durationMinutes) {
}
