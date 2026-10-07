package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.ProviderRequest;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.service.ProviderService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderService providerService;

    @PostMapping
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ProviderProfile> createProviderProfile(@Valid @RequestBody ProviderRequest request){
        return ResponseEntity.status(201).body(providerService.createProviderProfile(request));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderProfile getCurrentProviderProfile(){
        return providerService.getCurrentProviderProfile();
    }

}
