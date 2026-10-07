package com.ga.HomeHub.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(@NotBlank String firstName, @NotBlank String lastName, String phoneNumber) {
}
