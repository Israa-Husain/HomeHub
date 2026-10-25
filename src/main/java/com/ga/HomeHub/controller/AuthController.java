package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.auth.*;
import com.ga.HomeHub.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name="Auth", description="HomeHub auth API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @Operation(summary="Register a new user")
    @ApiResponses(value={
            @ApiResponse(responseCode="201",description="Registration successful. Email verification required"),
            @ApiResponse(responseCode="400",description="Invalid registration details"),
            @ApiResponse(responseCode="403",description="Admin accounts cannot self-register"),
            @ApiResponse(responseCode="409",description="Email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request){
        authService.registerUser(request);
        return ResponseEntity.status(201).body(Map.of("message","Registration success. Verify your email."));
    }

    @Operation(summary="Verify user email")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Email verified successfully"),
            @ApiResponse(responseCode="400",description="Verification token is required"),
            @ApiResponse(responseCode="403",description="Verification token has expired"),
            @ApiResponse(responseCode="404",description="Verification token not found"),
            @ApiResponse(responseCode="409",description="Email already verified")
    })
    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified. You can login."));
    }

    @Operation(summary="Login to HomeHub")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Login successful; JWT token returned"),
            @ApiResponse(responseCode="400",description="Invalid login request"),
            @ApiResponse(responseCode="401",description="Invalid email or password"),
            @ApiResponse(responseCode="403",description="Account inactive or email not verified")
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request){
        return authService.loginUser(request);
    }

    @Operation(summary="Request a password reset")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Password reset request processed; email sent if account exists"),
            @ApiResponse(responseCode="400",description="Invalid email address")
    })
    @PostMapping("/forgot-password")
    public Map<String,String> forgot(@Valid @RequestBody ForgotPasswordRequest request){
        authService.requestPasswordReset(request.email());
        return Map.of("message","Sent reset if the account exist");
    }

    @Operation(summary="Reset user password")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Password reset successfully"),
            @ApiResponse(responseCode="400",description="Invalid request or expired or already used reset token"),
            @ApiResponse(responseCode="404",description="Reset token not found")
    })
    @PostMapping("/reset-password")
    public Map<String,String> reset(@Valid @RequestBody ResetPasswordRequest request){
        authService.resetPassword(request);
        return Map.of("message","Password reset successful");
    }
}
