package com.ga.HomeHub.dto.auth;

public record ResetPasswordRequest(String token, String newPassword) {
}
