package com.ga.HomeHub.booking.controller;

import com.ga.HomeHub.booking.dto.ProviderResponse;
import com.ga.HomeHub.booking.dto.user.UserResponse;
import com.ga.HomeHub.booking.model.ProviderProfile;
import com.ga.HomeHub.booking.model.enums.ProviderStatus;
import com.ga.HomeHub.booking.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.HashMap;

@Tag(name="Admin", description="HomeHub admin API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final ProviderService providerService;

    @Operation(summary="List provider applications and optionally filter by existing status")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request parameters"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="503",description="Required account service is unavailable")
    })
    @GetMapping("/providers")
    public Page<Map<String,Object>> getProviderApplications(@RequestParam(required=false) ProviderStatus status,Pageable pageable){
        return providerService.getProviderApplications(status,pageable).map(this::applicationResponse);
    }

    @Operation(summary="Inspect provider application before approval")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request parameters"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Required account service is unavailable")
    })
    @GetMapping("/providers/{providerId}")
    public Map<String,Object> getProviderApplication(@PathVariable Long providerId){
        return applicationResponse(providerService.getProviderApplication(providerId));
    }

    @Operation(summary="Approve provider")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Provider approved successfully"),
            @ApiResponse(responseCode="400",description="Invalid provider request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Admin access required"),
            @ApiResponse(responseCode="404",description="Provider not found")
    })
    @PutMapping("/providers/{providerId}/approve")
    public ProviderResponse approveProvider(@PathVariable Long providerId){
        ProviderProfile provider = providerService.updateProviderStatus(providerId);
        return providerService.toResponse(provider);
    }

    private Map<String,Object> applicationResponse(ProviderProfile provider){
        Map<String,Object> result=new HashMap<>();
        result.put("provider",providerService.toResponse(provider));
        result.put("applicant",UserResponse.from(provider.getUser()));
        result.put("createdAt",provider.getCreatedAt());
        result.put("updatedAt",provider.getUpdatedAt());
        return result;
    }
}
