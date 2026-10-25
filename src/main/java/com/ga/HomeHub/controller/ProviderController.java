package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.ProviderRequest;
import com.ga.HomeHub.dto.ProviderResponse;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name="Provider", description="HomeHub provider API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderService providerService;

    @Operation(summary="Create provider profile")
    @PostMapping
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ProviderResponse> createProviderProfile(@Valid @RequestBody ProviderRequest request){
        ProviderProfile provider = providerService.createProviderProfile(request);
        return ResponseEntity.status(201).body(providerService.toResponse(provider));
    }

    @Operation(summary="Get current provider profile")
    @GetMapping("/profile")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse getCurrentProviderProfile(){
        return providerService.toResponse(providerService.getCurrentProviderProfile());
    }

    public record PortfolioRequest(String description,boolean emergencyEnabled){}
    @Operation(summary="Update portfolio")
    @PutMapping("/portfolio")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse updatePortfolio(@RequestBody PortfolioRequest request){
        return providerService.toResponse(providerService.updatePortfolio(request.description(),request.emergencyEnabled()));
    }

}
