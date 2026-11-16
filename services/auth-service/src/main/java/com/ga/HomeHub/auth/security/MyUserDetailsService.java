package com.ga.HomeHub.auth.security;

import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.UserStatus;
import com.ga.HomeHub.auth.repository.UserRepository;
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
        //Reject inactive accounts.
        if(user.getStatus()!= UserStatus.ACTIVE){
            throw new UsernameNotFoundException("Account is inactive");
        }
        return new MyUserDetails(user);
    }
}
