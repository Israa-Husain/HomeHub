package com.ga.HomeHub.notification.service;

import com.ga.HomeHub.notification.exception.ApiException;
import com.ga.HomeHub.notification.security.MyUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService{
    public MyUserDetails getCurrentUser(){
        Authentication authentication=
                SecurityContextHolder.getContext().getAuthentication();

        if(authentication==null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof MyUserDetails user)){
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        return user;
    }
}