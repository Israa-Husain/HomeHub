package com.ga.HomeHub.booking.controller;

import com.ga.HomeHub.booking.dto.ServiceRequest;
import com.ga.HomeHub.booking.dto.ServiceResponse;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.service.ServiceOfferingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @GetMapping
    public Page<ServiceResponse> getAllServiceOffering(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long providerId, Pageable pageable){
        return serviceOfferingService.getAllServiceOffering(categoryId, providerId, pageable);
    }

    //Providers can manage their own offerings regardless of active status.
    @Operation(summary="Get current provider services including inactive offerings")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @GetMapping("/mine")
    @PreAuthorize("hasRole('PROVIDER')")
    public Page<ServiceResponse> getCurrentProviderServices(Pageable pageable){
        return serviceOfferingService.getCurrentProviderServices(pageable);
    }

    @Operation(summary="Get service offering by id")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
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
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @GetMapping("/search")
    public Page<ServiceResponse> searchServices(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            Pageable pageable){

        return serviceOfferingService.searchServices(name, categoryId, providerId, minPrice, maxPrice, pageable);
    }
}
