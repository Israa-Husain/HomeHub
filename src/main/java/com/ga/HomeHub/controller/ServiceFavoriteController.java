package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.ServiceFavorite;
import com.ga.HomeHub.service.ServiceFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name="ServiceFavorite", description="HomeHub servicefavorite API endpoints")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/favorites")
@PreAuthorize("hasRole('HOMEOWNER')")
public class ServiceFavoriteController {
    private final ServiceFavoriteService service;

    @Operation(summary="Add favorite")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping("/{serviceId}")
    public Map<String,Object> addFavorite(@PathVariable Long serviceId){
        return response(service.addFavorite(serviceId));
    }

    @Operation(summary="Get my favorites")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping
    public List<Map<String,Object>> getMyFavorites(){
        return service.getMyFavorite().stream().map(this::response).toList();
    }

    @Operation(summary="Removefavorite")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @DeleteMapping("/{favoriteId}")
    public void removefavorite(@PathVariable Long favoriteId){
        service.removeFavorite(favoriteId);
    }

    private Map<String,Object> response(ServiceFavorite favorite){
        return Map.of("id",favorite.getId(), "serviceId",favorite.getService().getId());
    }
}
