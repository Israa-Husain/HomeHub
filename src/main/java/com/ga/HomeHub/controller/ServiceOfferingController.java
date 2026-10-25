package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.ServiceRequest;
import com.ga.HomeHub.dto.ServiceResponse;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.service.ServiceOfferingService;
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
import org.springframework.web.bind.annotation.*;

@Tag(name="ServiceOffering", description="HomeHub serviceoffering API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/services")
public class ServiceOfferingController {
    private final ServiceOfferingService serviceOfferingService;

    @Operation(summary="Get all service offering")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping
    public Page<ServiceResponse> getAllServiceOffering(@RequestParam(required = false) Long categoryId, Pageable pageable){
        return serviceOfferingService.getAllServiceOffering(categoryId, pageable);
    }

    @Operation(summary="Get service offering by id")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping("/{serviceId}")
    public ServiceResponse getServiceOfferingById(@PathVariable Long serviceId){
        return serviceOfferingService.getServiceResponseById(serviceId);
    }

    @Operation(summary="Create service offering")
    @ApiResponses(value={
            @ApiResponse(responseCode="201",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ServiceResponse> createServiceOffering(@Valid @RequestBody ServiceRequest request){
        ServiceOffering service = serviceOfferingService.createServiceOffering(request);
        return ResponseEntity.status(201).body(serviceOfferingService.toResponse(service));
    }

    @Operation(summary="Update service offering")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PutMapping("/{serviceId}")
    @PreAuthorize("hasRole('PROVIDER')")
    public ServiceResponse updateServiceOffering(@PathVariable Long serviceId, @Valid @RequestBody ServiceRequest request){
        ServiceOffering service = serviceOfferingService.updateServiceOffering(serviceId, request);
        return serviceOfferingService.toResponse(service);
    }

    @Operation(summary="Deactivate service offering")
    @ApiResponses(value={
            @ApiResponse(responseCode="204",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @DeleteMapping("/{serviceId}")
    @PreAuthorize("hasRole('PROVIDER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateServiceOffering(@PathVariable Long serviceId){
        serviceOfferingService.deactivateServiceOffering(serviceId);
    }

    @Operation(summary="Search services")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping("/search")
    public Page<ServiceResponse> searchServices(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            Pageable pageable){

        return serviceOfferingService.searchServices(name, categoryId, minPrice, maxPrice, pageable);
    }
}
