package com.ga.HomeHub.dto.auth;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
