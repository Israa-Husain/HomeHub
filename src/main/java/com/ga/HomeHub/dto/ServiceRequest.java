package com.ga.HomeHub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceRequest(@NotNull Long categoryId, @NotBlank String name, String description, @NotNull Double price, @NotNull Integer durationMinutes) {
}
