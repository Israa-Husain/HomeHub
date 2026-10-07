package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.ServiceRequest;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.model.enums.ServiceStatus;
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
        return repository.save(service);
    }

    public Page<ServiceOffering> getAllServiceOffering(Long categoryId, Pageable p){
        return categoryId == null ? repository.findByStatus(ServiceStatus.ACTIVE,p) : repository.findByCategoryIdAndStatus(categoryId, ServiceStatus.ACTIVE, p);
    }

    public ServiceOffering getServiceOfferingById(Long id){
        return repository.findById(id).orElseThrow(()-> new InformationNotFoundException("Service not found"));
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
        return repository.save(service);
    }

    public void deactivateServiceOffering(Long id){
        ServiceOffering service = getServiceOfferingById(id);
        if(!service.getProvider().getId().equals(providers.getCurrentProviderProfile().getId())){
            throw new UnauthorizedException("Not your service");
        }
        service.setStatus(ServiceStatus.INACTIVE);
        repository.save(service);
    }
}
