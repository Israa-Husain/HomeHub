package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.ProviderRequest;
import com.ga.HomeHub.dto.ProviderResponse;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses(value={
            @ApiResponse(responseCode="201",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ProviderResponse> createProviderProfile(@Valid @RequestBody ProviderRequest request){
        ProviderProfile provider = providerService.createProviderProfile(request);
        return ResponseEntity.status(201).body(providerService.toResponse(provider));
    }

    @Operation(summary="Get current provider profile")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping("/profile")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse getCurrentProviderProfile(){
        return providerService.toResponse(providerService.getCurrentProviderProfile());
    }

    //Only the current provider can edit their business profile.
    @Operation(summary="Update current provider business profile")
    @PutMapping("/profile")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse updateProviderProfile(@Valid @RequestBody ProviderRequest request){
        return providerService.toResponse(providerService.updateProviderProfile(request));
    }

    @Operation(summary="Get approved provider business details")
    @GetMapping("/{providerId}")
    public ProviderResponse getProviderDetails(@PathVariable Long providerId){
        return providerService.getProviderDetails(providerId);
    }

    public record PortfolioRequest(String description,boolean emergencyEnabled){}
    @Operation(summary="Update portfolio")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PutMapping("/portfolio")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse updatePortfolio(@RequestBody PortfolioRequest request){
        return providerService.toResponse(providerService.updatePortfolio(request.description(),request.emergencyEnabled()));
    }

}
