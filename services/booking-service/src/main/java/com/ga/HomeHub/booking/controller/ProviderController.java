package com.ga.HomeHub.booking.controller;

import com.ga.HomeHub.booking.dto.ProviderRequest;
import com.ga.HomeHub.booking.dto.ProviderResponse;
import com.ga.HomeHub.booking.model.ProviderProfile;
import com.ga.HomeHub.booking.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @GetMapping("/profile")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse getCurrentProviderProfile(){
        return providerService.toResponse(providerService.getCurrentProviderProfile());
    }

    //Only the current provider can edit their business profile.
    @Operation(summary="Update current provider business profile")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @PutMapping("/profile")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse updateProviderProfile(@Valid @RequestBody ProviderRequest request){
        return providerService.toResponse(providerService.updateProviderProfile(request));
    }

    @Operation(summary="Get approved provider business details")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @PutMapping("/portfolio")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderResponse updatePortfolio(@RequestBody PortfolioRequest request){
        return providerService.toResponse(providerService.updatePortfolio(request.description(),request.emergencyEnabled()));
    }

}
