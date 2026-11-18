package com.ga.HomeHub.booking.dto;

public record ProviderResponse(
        Long id,
        String businessName,
        String description,
        String providerStatus,
        String portfolioDescription,
        boolean emergencyEnabled
) {
}
