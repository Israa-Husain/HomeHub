package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.auth.LoginRequest;
import com.ga.HomeHub.dto.auth.LoginResponse;
import com.ga.HomeHub.dto.auth.RegisterRequest;
import com.ga.HomeHub.dto.auth.ResetPasswordRequest;
import com.ga.HomeHub.exception.InformationExistException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.EmailVerificationToken;
import com.ga.HomeHub.model.PasswordResetToken;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.repository.EmailVerificationTokenRepository;
import com.ga.HomeHub.repository.PasswordResetTokenRepository;
import com.ga.HomeHub.repository.UserRepository;
import com.ga.HomeHub.security.JWTUtils;
import com.ga.HomeHub.security.MyUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final EmailVerificationTokenRepository verifyRepository;
    private final PasswordResetTokenRepository resetRepository;
    private final PasswordEncoder encoder;
    private final AuthenticationManager auth;
    private final EmailService email;
    private final JWTUtils jwt;

    @Value("${app.base-url}")
    private String baseUrl;

    public void registerUser(RegisterRequest request){
        if(users.existsByEmailAddress(request.email().toLowerCase())){
            throw new InformationExistException("Email already registered");
        }
        if(request.role() == Role.ADMIN){
            throw new UnauthorizedException("Admin account can not self-register");
        }
        User user = new User();
        user.setFirstName(request.firstname());
        user.setLastName(request.lastName());
        user.setEmailAddress(request.email().toLowerCase());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(request.role());
        users.save(user);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiredAt(LocalDateTime.now().plusHours(24));
        verifyRepository.save(token);
        String verificationLink = baseUrl + "/api/auth/verify?token=" + token.getToken();
        email.sendEmail(user.getEmailAddress(), "Verify your HomeHub account", "Welcome to HomeHub!\n\n" + "Please verify your email by clicking the link below:\n\n" + verificationLink + "\n\nThis verification link expires in 24 hours.");
    }

//    public void verifyEmail(String token){
//        EmailVerificationToken t = verifyRepository.findByToken(token).orElseThrow(()-> new InformationNotFoundException("Verification token not found"));
//        if(t.getUsedAt() != null || t.isExpired()){
//            throw new UnauthorizedException("Verification token is invalid or expired");
//        }
//        t.getUser().setEmailVerified(true);
//        users.save(t.getUser());
//        t.markUsed();
//        verifyRepository.save(t);
//    }

    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = verifyRepository.findByToken(token).orElseThrow(() -> new InformationNotFoundException("Invalid verification token"));

        if(verificationToken.getUsedAt() != null){
            throw new InformationExistException("Email already verified");
        }
        if(verificationToken.getExpiredAt().isBefore(LocalDateTime.now())){
            throw new UnauthorizedException("Verification token expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        users.save(user);
        verificationToken.setUsedAt(LocalDateTime.now());
        verifyRepository.save(verificationToken);
    }

    public LoginResponse loginUser(LoginRequest request){
        User user = users.findUserByEmailAddress(request.email().toLowerCase());
        if(user == null){
            throw new UnauthorizedException("Invalid credentials");
        }
        if(user.getStatus() != UserStatus.ACTIVE){
            throw new UnauthorizedException("Inactive Account");
        }
        if(!user.isEmailVerified()){
            throw new UnauthorizedException("Verify email before login");
        }
        try{
            auth.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e){
            throw new UnauthorizedException("Invalid credentials");
        }
        MyUserDetails userDetails = new MyUserDetails(user);
        return new LoginResponse(jwt.generateJwtToken(userDetails));
    }

    public void requestPasswordReset(String address) {
        User user = users.findUserByEmailAddress(address.toLowerCase());
        if (user != null) {
            PasswordResetToken token = new PasswordResetToken();

            token.setUser(user);
            token.setToken(UUID.randomUUID().toString());
            token.setExpiredAt(LocalDateTime.now().plusMinutes(30));

            resetRepository.save(token);

            email.sendEmail(user.getEmailAddress(), "HomeHub password reset", "Reset token: " + token.getToken());
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken t = resetRepository.findByToken(request.token()).orElseThrow(() -> new InformationNotFoundException("Reset token not found"));
        if (t.getUsedAt() != null || t.isExpired()){
            throw new UnauthorizedException("Reset token is invalid or expired");
        }
        t.getUser().setPasswordHash(encoder.encode(request.newPassword()));
        users.save(t.getUser());
        t.markUsed();
        resetRepository.save(t);
    }
}
