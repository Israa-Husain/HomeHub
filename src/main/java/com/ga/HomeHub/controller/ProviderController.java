package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.ProviderRequest;
import com.ga.HomeHub.dto.ProviderResponse;
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
    public ResponseEntity<ProviderResponse> createProviderProfile(@Valid @RequestBody ProviderRequest request){
        ProviderProfile provider = providerService.createProviderProfile(request);
        return ResponseEntity.status(201).body(providerService.toResponse(provider));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse getCurrentProviderProfile(){
        return providerService.toResponse(providerService.getCurrentProviderProfile());
    }

}
