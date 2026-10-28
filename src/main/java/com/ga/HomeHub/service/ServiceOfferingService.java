package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.ServiceRequest;
import com.ga.HomeHub.dto.ServiceResponse;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.CategoryStatus;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.ServiceStatus;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.repository.CategoryRepository;
import com.ga.HomeHub.repository.ServiceOfferingRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ServiceOfferingService {
    private final ServiceOfferingRepository repository;
    private final CategoryRepository category;
    private final ProviderService providers;
    private final AuditLogService audit;
    private final CurrentUserService current;

    public ServiceOffering createServiceOffering(ServiceRequest request){
        ProviderProfile provider = providers.getCurrentProviderProfile();
        if(provider.getProviderStatus() != ProviderStatus.APPROVED){
            throw new UnauthorizedException("Provider must be approved");
        }
        ServiceOffering service = new ServiceOffering();
        service.setProvider(provider);
        service.setCategory(category.findById(request.categoryId()).orElseThrow(()->new InformationNotFoundException("Category not found")));
        service.setName(request.name());
        service.setDescription(request.description());
        service.setPrice(request.price());
        service.setDurationMinutes(request.durationMinutes());
        service.setEmergencyEnabled(Boolean.TRUE.equals(request.emergencyEnabled()));
        ServiceOffering savedService = repository.save(service);
        audit.record(provider.getUser(), "Create Service", "ServiceOffering", savedService.getId(), "Provider created service " + savedService.getName());
        return savedService;
    }

    public Page<ServiceResponse> getAllServiceOffering(Long categoryId, Long providerId, Pageable p){
        return repository.searchServices(null,categoryId,providerId,null,null,ServiceStatus.ACTIVE,p).map(this::toResponse);
    }

    public ServiceResponse getServiceResponseById(Long id){
        ServiceOffering service=getServiceOfferingById(id);
        User user=current.getCurrentUser();
        boolean owner=service.getProvider().getUser().getId().equals(user.getId());
        if(!isDiscoverable(service) && !owner && user.getRole()!=Role.ADMIN){
            throw new InformationNotFoundException("Service unavailable");
        }
        return toResponse(service);
    }

    //Resolve ownership from the current account rather than accepting a provider ID from the caller.
    public Page<ServiceResponse> getCurrentProviderServices(Pageable pageable){
        return repository.findByProviderId(providers.getCurrentProviderProfile().getId(),pageable).map(this::toResponse);
    }

    public ServiceOffering getServiceOfferingById(Long id){
        return repository.findById(id).orElseThrow(()-> new InformationNotFoundException("Service not found"));
    }

    public Page<ServiceResponse> searchServices(String name, Long categoryId, Long providerId, Double minPrice, Double maxPrice, Pageable pageable){
        if(minPrice != null && minPrice < 0){
            throw new IllegalArgumentException("Minimum price cannot be negative");
        }
        if(maxPrice != null && maxPrice < 0){
            throw new IllegalArgumentException("Maximum price cannot be negative");
        }
        if(minPrice != null && maxPrice != null && minPrice > maxPrice){
            throw new IllegalArgumentException("Minimum price cannot exceed maximum price");
        }

        return repository.searchServices(name, categoryId, providerId, minPrice, maxPrice, ServiceStatus.ACTIVE, pageable).map(this::toResponse);
    }

    public ServiceOffering updateServiceOffering(Long id, ServiceRequest request){
        ServiceOffering service = getServiceOfferingById(id);
        if(!service.getProvider().getId().equals(providers.getCurrentProviderProfile().getId())){
            throw new UnauthorizedException("Not your service");
        }
        service.setCategory(category.findById(request.categoryId()).orElseThrow(()-> new InformationNotFoundException("Category not found")));
        service.setName(request.name());
        service.setDescription(request.description());
        service.setPrice(request.price());
        service.setDurationMinutes(request.durationMinutes());
        //Preserve existing opt-in when older clients omit the optional field.
        if(request.emergencyEnabled()!=null){
            service.setEmergencyEnabled(request.emergencyEnabled());
        }
        ServiceOffering savedService = repository.save(service);
        audit.record(service.getProvider().getUser(), "Update Service", "ServiceOffering", savedService.getId(), "Provider Updated Service " + savedService.getName());
        return savedService;
    }

    public void deactivateServiceOffering(Long id){
        ServiceOffering service = getServiceOfferingById(id);
        if(!service.getProvider().getId().equals(providers.getCurrentProviderProfile().getId())){
            throw new UnauthorizedException("Not your service");
        }
        service.setStatus(ServiceStatus.INACTIVE);
        repository.save(service);
        audit.record(service.getProvider().getUser(), "Deactivate Service", "ServiceOffering", service.getId(), "Provider Deactivated Service " + service.getName());
    }

    //Discovery eligibility does not reserve a slot or replace booking-time validation.
    private boolean isDiscoverable(ServiceOffering service){
        return service.getStatus()==ServiceStatus.ACTIVE
                && service.getCategory().getStatus()==CategoryStatus.ACTIVE
                && service.getProvider().getProviderStatus()==ProviderStatus.APPROVED
                && service.getProvider().getUser().getStatus()==UserStatus.ACTIVE;
    }

    public ServiceResponse toResponse(ServiceOffering service){
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getDurationMinutes(),
                service.getCategory().getName(),
                service.getProvider().getBusinessName(),
                service.getCategory().getId(),
                service.getProvider().getId(),
                service.getStatus().name(),
                service.getCategory().getStatus().name(),
                service.getProvider().getProviderStatus().name(),
                service.isEmergencyEnabled(),
                service.getProvider().isEmergencyEnabled(),
                isDiscoverable(service) && service.isEmergencyEnabled() && service.getProvider().isEmergencyEnabled()
        );
    }
}
