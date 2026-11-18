package com.ga.HomeHub.notification.service;

import com.ga.HomeHub.notification.client.AuthServiceClient;
import com.ga.HomeHub.notification.exception.ApiException;
import com.ga.HomeHub.notification.security.JWTUtils;
import com.ga.HomeHub.notification.security.MyUserDetails;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AccountVerificationService{
    private final JWTUtils jwt;
    private final AuthServiceClient auth;

    public AccountVerificationService(JWTUtils jwt,AuthServiceClient auth){
        this.jwt=jwt;
        this.auth=auth;
    }

    public MyUserDetails verify(String bearer){
        if(bearer==null || !bearer.startsWith("Bearer ")){
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        String subject;

        try{
            subject=jwt.subject(bearer.substring(7));
        }catch(RuntimeException e){
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token"
            );
        }

        AuthServiceClient.Account account;

        try{
            account=auth.current(bearer);
        }catch(FeignException.Unauthorized | FeignException.Forbidden e){
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Account is unavailable"
            );
        }catch(RuntimeException e){
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Account verification unavailable"
            );
        }

        if(account==null
                || account.id()==null
                || account.id()<=0
                || account.email()==null
                || !subject.equalsIgnoreCase(account.email())
                || !"ACTIVE".equals(account.status())
                || !account.isEmailVerified()
                || !Set.of("HOMEOWNER","PROVIDER","ADMIN")
                .contains(account.role()==null?"":account.role())){
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Account is unavailable"
            );
        }

        return new MyUserDetails(
                account.id(),
                account.email(),
                account.role()
        );
    }
}