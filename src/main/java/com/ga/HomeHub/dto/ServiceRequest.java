package com.ga.HomeHub.dto;

public record ServiceRequest(Long categoryId, String name, String description, Double price, Integer durationMinutes) {
}
