package com.ga.HomeHub.security;

import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.UserStatus;
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
    private final UserRepository userRepository;
    @Override
    public UserDetails loadUserByUsername(String emailAddress) throws UsernameNotFoundException {
        User user = userRepository.findUserByEmailAddress(emailAddress);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        //auth deactivated users
        if(user.getStatus()!= UserStatus.ACTIVE){
            throw new UsernameNotFoundException("Account is inactive");
        }
        return new MyUserDetails(user);
    }
}
