package com.ga.HomeHub.booking.service;

import com.ga.HomeHub.booking.exception.AuthenticationFailedException;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import com.ga.HomeHub.booking.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Transactional(readOnly=true)
public class CurrentUserService {
    private final UserRepository userRepository;

    //Returns the currently authenticated HomeHub user.
    public User getCurrentUser(){
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();

        //Reject missing or anonymous authentication before accessing the account.
        if(authentication==null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken){
            throw new AuthenticationFailedException("Authentication required");
        }

        String emailAddress=authentication.getName();
        User user = userRepository.findUserByEmailAddress(emailAddress);

        //Recheck account status for each authenticated operation.
        if(user==null || user.getStatus()!=UserStatus.ACTIVE){
            throw new UnauthorizedException("Account is inactive or unavailable");
        }

        return user;
    }
}