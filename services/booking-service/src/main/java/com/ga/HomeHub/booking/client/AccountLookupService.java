package com.ga.HomeHub.booking.client;

import com.ga.HomeHub.booking.dto.user.UserResponse;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import com.ga.HomeHub.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountLookupService{
    private final UserRepository users;

    //Resolve the existing shared account without inserting or updating users.
    @Transactional(readOnly=true)
    public User resolve(UserResponse response){
        if(response==null || response.id()==null || response.email()==null || response.role()==null || response.status()!=UserStatus.ACTIVE || !response.isEmailVerified()){
            throw new UnauthorizedException("Account is inactive or unavailable");
        }

        User user=users.findById(response.id()).orElseThrow(()->new UnauthorizedException("Account is unavailable"));

        //Reject account details that changed between Auth verification and this lookup.
        if(user.getStatus()!=UserStatus.ACTIVE || !user.isEmailVerified() || user.getRole()!=response.role() || !response.email().equalsIgnoreCase(user.getEmailAddress())){
            throw new UnauthorizedException("Account verification failed");
        }

        return user;
    }
}