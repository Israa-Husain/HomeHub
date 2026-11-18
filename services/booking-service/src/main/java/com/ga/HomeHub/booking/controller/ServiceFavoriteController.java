package com.ga.HomeHub.booking.controller;

import com.ga.HomeHub.booking.model.ServiceFavorite;
import com.ga.HomeHub.booking.service.ServiceFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @DeleteMapping("/{favoriteId}")
    public void removefavorite(@PathVariable Long favoriteId){
        service.removeFavorite(favoriteId);
    }

    private Map<String,Object> response(ServiceFavorite favorite){
        return Map.of("id",favorite.getId(), "serviceId",favorite.getService().getId());
    }
}
