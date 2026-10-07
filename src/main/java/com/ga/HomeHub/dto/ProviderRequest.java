package com.ga.HomeHub.dto;

import jakarta.validation.constraints.NotBlank;

public record ProviderRequest(@NotBlank String businessName, String description) {
}
