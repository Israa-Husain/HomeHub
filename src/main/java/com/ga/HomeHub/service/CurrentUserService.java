package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CurrentUserService {
    private final UserRepository userRepository;

    ////returns the currently authenticated HomeHub user.
    public User getCurrentUser(){
        String emailAddress = SecurityContextHolder.getContext().getAuthentication().getName();
        User user=userRepository.findUserByEmailAddress(emailAddress);

        //Recheck account status for each authenticated operation.
        if(user==null || user.getStatus()!= UserStatus.ACTIVE){
            throw new UnauthorizedException("Account is inactive or unavailable");
        }
        return user;
    }
}
