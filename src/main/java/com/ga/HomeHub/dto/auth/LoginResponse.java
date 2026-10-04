package com.ga.HomeHub.dto.auth;

public record LoginResponse(String token, String type) {
    public LoginResponse(String token){
        this(token, "Bearer");
    }
}
