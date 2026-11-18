package com.ga.HomeHub.auth.dto.auth;

public record LoginResponse(String token, String type) {
    public LoginResponse(String token) {
        this(token, "Bearer");
    }
}
