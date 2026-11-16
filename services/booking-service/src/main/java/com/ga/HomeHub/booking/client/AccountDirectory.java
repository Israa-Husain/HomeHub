package com.ga.HomeHub.booking.client;

import com.ga.HomeHub.booking.model.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountDirectory {
    private final AuthServiceClient auth;

    @Value("${homehub.internal-key}")
    private String key;

    //Callers fail closed if the owner service cannot provide current status.
    public boolean isActive(Long id) {
        try {
            return auth.byId(id, key).status() == UserStatus.ACTIVE;
        } catch (feign.FeignException.NotFound e) {
            return false;
        }
    }
}