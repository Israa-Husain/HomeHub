package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.user.UserResponse;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.service.ProviderService;
import com.ga.HomeHub.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserService userService;
    private final ProviderService providerService;

    @GetMapping("/users")
    public Page<UserResponse> getAllUsers(Pageable pageable){
        return userService.getAllUsers(pageable);
    }

    @PutMapping("/providers/{providerId}/approve")
    public ProviderProfile approveProvider(@PathVariable Long providerId){
        return providerService.updateProviderStatus(providerId);
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateUser(@PathVariable Long userId){
        userService.deactivate(userId);
    }
}
