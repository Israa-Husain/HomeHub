package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.ProviderResponse;
import com.ga.HomeHub.dto.user.UserResponse;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.service.ProviderService;
import com.ga.HomeHub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
@Tag(name="Admin", description="HomeHub admin API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserService userService;
    private final ProviderService providerService;

    @Operation(summary="Get all users")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Users retrieved successfully"),
            @ApiResponse(responseCode="400",description="Invalid pagination parameters"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Admin access required")
    })
    @GetMapping("/users")
    public Page<UserResponse> getAllUsers(Pageable pageable){
        return userService.getAllUsers(pageable);
    }

    @Operation(summary="List provider applications and optionally filter by existing status")
    @GetMapping("/providers")
    public Page<Map<String,Object>> getProviderApplications(@RequestParam(required=false) ProviderStatus status,Pageable pageable){
        return providerService.getProviderApplications(status,pageable).map(this::applicationResponse);
    }

    @Operation(summary="Inspect provider application before approval")
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

    @Operation(summary="Deactivate user")
    @ApiResponses(value={
            @ApiResponse(responseCode="204",description="User deactivated successfully"),
            @ApiResponse(responseCode="400",description="Invalid user request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Admin access required"),
            @ApiResponse(responseCode="404",description="User not found")
    })
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateUser(@PathVariable Long userId){
        userService.deactivate(userId);
    }

    //Reuse existing safe response mappings; never serialize the applicant's User entity.
    private Map<String,Object> applicationResponse(ProviderProfile provider){
        Map<String,Object> result=new HashMap<>();
        result.put("provider",providerService.toResponse(provider));
        result.put("applicant",UserResponse.from(provider.getUser()));
        result.put("createdAt",provider.getCreatedAt());
        result.put("updatedAt",provider.getUpdatedAt());
        return result;
    }
}
