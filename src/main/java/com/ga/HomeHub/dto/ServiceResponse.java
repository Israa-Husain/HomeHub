package com.ga.HomeHub.dto;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        Double price,
        Integer durationMinutes,
        String categoryName,
        String providerName
) {
}