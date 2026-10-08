package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.ServiceRequest;
import com.ga.HomeHub.dto.ServiceResponse;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.service.ServiceOfferingService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/services")
public class ServiceOfferingController {
    private final ServiceOfferingService serviceOfferingService;

    @GetMapping
    public Page<ServiceResponse> getAllServiceOffering(@RequestParam(required = false) Long categoryId, Pageable pageable){
        return serviceOfferingService.getAllServiceOffering(categoryId, pageable);
    }

    @GetMapping("/{serviceId}")
    public ServiceResponse getServiceOfferingById(@PathVariable Long serviceId){
        return serviceOfferingService.getServiceResponseById(serviceId);
    }

    @PostMapping
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ServiceResponse> createServiceOffering(@Valid @RequestBody ServiceRequest request){
        ServiceOffering service = serviceOfferingService.createServiceOffering(request);
        return ResponseEntity.status(201).body(serviceOfferingService.toResponse(service));
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize("hasRole('PROVIDER')")
    public ServiceResponse updateServiceOffering(@PathVariable Long serviceId, @Valid @RequestBody ServiceRequest request){
        ServiceOffering service = serviceOfferingService.updateServiceOffering(serviceId, request);
        return serviceOfferingService.toResponse(service);
    }

    @DeleteMapping("/{serviceId}")
    @PreAuthorize("hasRole('PROVIDER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateServiceOffering(@PathVariable Long serviceId){
        serviceOfferingService.deactivateServiceOffering(serviceId);
    }

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
