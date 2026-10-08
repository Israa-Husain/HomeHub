package com.ga.HomeHub.dto;

public record ProviderResponse(
        Long id,
        String businessName,
        String description,
        String providerStatus
) {
}