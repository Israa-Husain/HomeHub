package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.ProviderRequest;
import com.ga.HomeHub.exception.InformationExistException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.logging.Level;
import java.util.logging.Logger;
@Service
@AllArgsConstructor
public class ProviderService {

    private final ProviderProfileRepository repository;
    private final CurrentUserService current;
    private final AuditLogService audit;

    private final Logger logger = Logger.getLogger(ProviderService.class.getName());


    public ProviderProfile createProviderProfile(ProviderRequest request){
        User user = current.getCurrentUser();
        if(user.getRole() != Role.PROVIDER){
            throw new UnauthorizedException("Provider role required");
        }
        if(repository.findByUserId(user.getId()).isPresent()){
            throw new InformationExistException("Provider profile already exist");
        }
        ProviderProfile provider = new ProviderProfile();
        provider.setUser(user);
        provider.setBusinessName(request.businessName());
        provider.setDescription(request.description());

        ProviderProfile savedProvider = repository.save(provider);
        audit.record(user, "Create Provider Profile", "ProviderProfile", savedProvider.getId(), "Provider created profile");

        return savedProvider;
    }

    public ProviderProfile getCurrentProviderProfile(){
        return repository.findByUserId(current.getCurrentUser().getId()).orElseThrow(()-> new InformationNotFoundException("Provider profile not found"));
    }

    public ProviderProfile updateProviderStatus(Long id){
        ProviderProfile provider = repository.findById(id).orElseThrow(()-> new InformationNotFoundException("Provider not found"));
        provider.setProviderStatus(ProviderStatus.APPROVED);
        ProviderProfile savedProvider = repository.save(provider);
        logger.log(Level.INFO, "Admin approved provider");
        audit.record(current.getCurrentUser(), "Approve Provider", "ProviderProfile", savedProvider.getId(), "Admin approved provider");
        return savedProvider;
    }

}
