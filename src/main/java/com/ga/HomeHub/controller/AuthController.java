package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.auth.*;
import com.ga.HomeHub.service.AuthService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request){
        authService.registerUser(request);
        return ResponseEntity.status(201).body(Map.of("message","Registration success. Verify your email."));
    }

    @GetMapping("/verify")
    public Map<String,String> verify(@RequestParam String token){
        authService.verifyEmail(token);
        return Map.of("message","Email verified");
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request){
        return authService.loginUser(request);
    }

    @PostMapping("/forgot-password")
    public Map<String,String> forgot(@RequestBody ForgotPasswordRequest request){
        authService.requestPasswordReset(request.email());
        return Map.of("message","Sent reset if the account exist");
    }

    @PostMapping("/reset-password")
    public Map<String,String> reset(@RequestBody ResetPasswordRequest request){
        authService.resetPassword(request);
        return Map.of("message","Password reset successful");
    }
}
