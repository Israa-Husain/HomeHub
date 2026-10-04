package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.auth.RegisterRequest;
import com.ga.HomeHub.exception.InformationExistException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.EmailVerificationToken;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.repository.EmailVerificationTokenRepository;
import com.ga.HomeHub.repository.PasswordResetTokenRepository;
import com.ga.HomeHub.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final EmailVerificationTokenRepository verifyRepository;
    private final PasswordResetTokenRepository resetRepository;
    private final PasswordEncoder encoder;
    private final AuthenticationManager auth;
    private final EmailService email;
//    private final JWTUtils jwt;

    public void registerUser(RegisterRequest request){
        if(users.existsByEmailAddress(request.email())){
            throw new InformationExistException("Email already registered");
        }
        if(request.role() == Role.ADMIN){
            throw new UnauthorizedException("Admin account can not self-register");
        }
        User user = new User();
        user.setFirstName(request.firstname());
        user.setLastName(request.lastName());
        user.setEmail(request.email().toLowerCase());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(request.role());
        users.save(user);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setExpiredAt(LocalDateTime.now().plusHours(24));
        verifyRepository.save(token);
        email.sendEmail(user.getEmail(), "Verify HomeHub account", "Verification token: "+token.getToken());
    }

    public void verifyEmail(String token){
        EmailVerificationToken t = verifyRepository.findByToken(token).orElseThrow(()-> new InformationNotFoundException("Verification token not found"));
        if(t.getUsedAt() != null || t.isExpired()){
            throw new UnauthorizedException("Verification token is invalid or expired");
        }
        t.getUser().setEmailVerified(true);
        users.save(t.getUser());
        t.markUsed();
        verifyRepository.save(t);
    }



    /*
    public void registerUser()
    public void verifyEmail()
    public LoginResponse loginUser()
    public void requestPasswordReset()
    public void resetPassword()
    * */
}
