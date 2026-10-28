package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.ProviderRequest;
import com.ga.HomeHub.dto.ProviderResponse;
import com.ga.HomeHub.exception.InformationExistException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import java.util.logging.Level;
import java.util.logging.Logger;
@Service
@RequiredArgsConstructor
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

    //AdminController restricts application reads; marketplace detail visibility remains unchanged.
    @Transactional(readOnly=true)
    public Page<ProviderProfile> getProviderApplications(ProviderStatus status,Pageable pageable){
        if(status==null){
            return repository.findAll(pageable);
        }
        return repository.findByProviderStatus(status,pageable);
    }

    @Transactional(readOnly=true)
    public ProviderProfile getProviderApplication(Long id){
        return repository.findById(id).orElseThrow(()->new InformationNotFoundException("Provider not found"));
    }

    //Return business information without exposing the linked user's private account fields.
    public ProviderResponse getProviderDetails(Long id){
        ProviderProfile provider=repository.findById(id).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        if(provider.getProviderStatus()!=ProviderStatus.APPROVED || provider.getUser().getStatus()!=UserStatus.ACTIVE){
            throw new InformationNotFoundException("Provider unavailable");
        }
        return toResponse(provider);
    }

    //Profile edits do not grant approval or change the provider's existing status.
    public ProviderProfile updateProviderProfile(ProviderRequest request){
        ProviderProfile provider=getCurrentProviderProfile();
        provider.setBusinessName(request.businessName());
        provider.setDescription(request.description());
        ProviderProfile savedProvider=repository.save(provider);
        audit.record(provider.getUser(),"Update Provider Profile","ProviderProfile",provider.getId(),"Provider updated business profile");
        return savedProvider;
    }

    public ProviderProfile updateProviderStatus(Long id){
        ProviderProfile provider = repository.findById(id).orElseThrow(()-> new InformationNotFoundException("Provider not found"));
        provider.setProviderStatus(ProviderStatus.APPROVED);
        ProviderProfile savedProvider = repository.save(provider);
        logger.log(Level.INFO, "Admin approved provider");
        audit.record(current.getCurrentUser(), "Approve Provider", "ProviderProfile", savedProvider.getId(), "Admin approved provider");
        return savedProvider;
    }

    //Providers can update their portfolio and emergency-booking availability.
    public ProviderProfile updatePortfolio(String portfolioDescribtion, boolean emergencyEnabled){
        ProviderProfile provider = getCurrentProviderProfile();
        if(portfolioDescribtion!=null && portfolioDescribtion.length()>3000){
            throw new IllegalArgumentException("Portfolio is too long");
        }
        provider.setPortfolioDescription(portfolioDescribtion);
        provider.setEmergencyEnabled(emergencyEnabled);
        return repository.save(provider);
    }

    public ProviderResponse toResponse(ProviderProfile provider){
        return new ProviderResponse(
                provider.getId(),
                provider.getBusinessName(),
                provider.getDescription(),
                provider.getProviderStatus().name(),
                provider.getPortfolioDescription(),
                provider.isEmergencyEnabled()
        );
    }

}
