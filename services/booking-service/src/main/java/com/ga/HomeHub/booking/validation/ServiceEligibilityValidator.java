package com.ga.HomeHub.booking.validation;
import com.ga.HomeHub.booking.model.enums.UserStatus;

import com.ga.HomeHub.booking.client.AccountDirectory;
import com.ga.HomeHub.booking.exception.BookingConflictException;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.enums.CategoryStatus;
import com.ga.HomeHub.booking.model.enums.ProviderStatus;
import com.ga.HomeHub.booking.model.enums.ServiceStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceEligibilityValidator{
    private final EntityManager entityManager;
    private final AccountDirectory accounts;

    //Call after the provider lock when creating, claiming or approving a changed appointment.
    public void refreshAndRequire(ServiceOffering service){
        entityManager.refresh(service);entityManager.refresh(service.getCategory());
        entityManager.refresh(service.getProvider());entityManager.refresh(service.getProvider().getUser());
        require(service);
    }

    public void require(ServiceOffering service){
        if(service.getStatus()!=ServiceStatus.ACTIVE || service.getCategory().getStatus()!=CategoryStatus.ACTIVE
                || service.getProvider().getProviderStatus()!=ProviderStatus.APPROVED
                || service.getProvider().getUser().getStatus()!=UserStatus.ACTIVE
                || !accounts.isActive(service.getProvider().getUser().getId())){
            throw new BookingConflictException("Service unavailable");
        }
    }
}