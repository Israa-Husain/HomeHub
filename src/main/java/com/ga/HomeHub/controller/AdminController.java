package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.ProviderResponse;
import com.ga.HomeHub.dto.user.UserResponse;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.service.ProviderService;
import com.ga.HomeHub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name="Admin", description="HomeHub admin API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserService userService;
    private final ProviderService providerService;

    @Operation(summary="Get all users")
    @GetMapping("/users")
    public Page<UserResponse> getAllUsers(Pageable pageable){
        return userService.getAllUsers(pageable);
    }

    @Operation(summary="Approve provider")
    @PutMapping("/providers/{providerId}/approve")
    public ProviderResponse approveProvider(@PathVariable Long providerId){
        ProviderProfile provider = providerService.updateProviderStatus(providerId);
        return providerService.toResponse(provider);
    }

    @Operation(summary="Deactivate user")
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateUser(@PathVariable Long userId){
        userService.deactivate(userId);
    }
}
