package com.ga.HomeHub.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name="auth-service",url="${homehub.auth-url}")
public interface AuthServiceClient{
    record Account(
            Long id,
            String email,
            String role,
            String status,
            boolean isEmailVerified
    ){}

    @GetMapping("/internal/users/current")
    Account current(@RequestHeader("Authorization") String bearer);
}