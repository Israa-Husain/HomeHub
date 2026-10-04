package com.ga.HomeHub.security;

import com.ga.HomeHub.model.User;
import com.ga.HomeHub.repository.UserRepository;
import com.ga.HomeHub.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MyUserDetailsService implements UserDetailsService {
//    private UserService userService;
    private final UserRepository userRepository;


//    @Override
//    public UserDetails loadUserByUsername(String emailAddress) throws UsernameNotFoundException {
//        User user = userService.;
//        return new MyUserDetails(user);
//    }

    @Override
    public UserDetails loadUserByUsername(String emailAddress) throws UsernameNotFoundException {
        User user = userRepository.findUserByEmailAddress(emailAddress);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new MyUserDetails(user);
    }
}
