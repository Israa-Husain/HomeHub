package com.ga.HomeHub.booking.client;

import com.ga.HomeHub.booking.dto.user.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name="auth-service",url="${homehub.auth-url}")
//Exchange safe account DTOs without exposing credentials to Booking.
public interface AuthServiceClient{
    @GetMapping("/internal/users/current")
    UserResponse current(@RequestHeader("Authorization") String bearer);

    @GetMapping("/internal/users/{id}")
    UserResponse byId(@PathVariable("id") Long id,@RequestHeader("X-Internal-Key") String key);
}