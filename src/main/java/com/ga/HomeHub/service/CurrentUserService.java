package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.repository.UserRepository;
<<<<<<< HEAD
=======
import lombok.AllArgsConstructor;
>>>>>>> feature/repositories
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
<<<<<<< HEAD
public class CurrentUserService {
    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

=======
@AllArgsConstructor
public class CurrentUserService {
    private final UserRepository userRepository;

>>>>>>> feature/repositories
    //returns the currently authenticated HomeHub user.
    public User getCurrentUser(){
        String emailAddress = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findUserByEmailAddress(emailAddress);
    }
}
