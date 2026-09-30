package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    //returns the currently authenticated HomeHub user.
    public User getCurrentUser(){
        String emailAddress = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findUserByEmailAddress(emailAddress);
    }
}
