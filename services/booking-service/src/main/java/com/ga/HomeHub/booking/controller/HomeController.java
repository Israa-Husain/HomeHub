package com.ga.HomeHub.booking.controller;

import com.ga.HomeHub.booking.dto.HomeRequest;
import com.ga.HomeHub.booking.model.Home;
import com.ga.HomeHub.booking.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Home", description="HomeHub home API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/homes")
public class HomeController {
    private final HomeService homeService;

    @Operation(summary="Create home")
    @ApiResponses(value={
            @ApiResponse(responseCode="201",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @PostMapping
    @PreAuthorize("hasRole('HOMEOWNER')")
    public ResponseEntity<Home> createHome(@Valid @RequestBody HomeRequest request){
        return ResponseEntity.status(201).body(homeService.createHome(request));
    }

    @Operation(summary="Get current user homes")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @GetMapping
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Page<Home> getCurrentUserHomes(Pageable pageable){
        return homeService.getCurrentUserHomes(pageable);
    }

    @Operation(summary="Get home by id")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @GetMapping("/{homeId}")
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Home getHomeById(@PathVariable Long homeId){
        return homeService.getHomeById(homeId);
    }

    @Operation(summary="Update home")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @PutMapping("/{homeId}")
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Home updateHome(@PathVariable Long homeId, @Valid @RequestBody HomeRequest request){
        return homeService.updateHome(homeId, request);
    }

    @Operation(summary="Delete home")
    @ApiResponses(value={
            @ApiResponse(responseCode="204",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @DeleteMapping("/{homeId}")
    @PreAuthorize("hasRole('HOMEOWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHome(@PathVariable Long homeId){
        homeService.deleteHome(homeId);
    }
}
