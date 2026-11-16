package com.ga.HomeHub.auth.service;

import com.ga.HomeHub.auth.dto.auth.LoginRequest;
import com.ga.HomeHub.auth.dto.auth.LoginResponse;
import com.ga.HomeHub.auth.dto.auth.RegisterRequest;
import com.ga.HomeHub.auth.dto.auth.ResetPasswordRequest;
import com.ga.HomeHub.auth.exception.AuthenticationFailedException;
import com.ga.HomeHub.auth.exception.InformationExistException;
import com.ga.HomeHub.auth.exception.InformationNotFoundException;
import com.ga.HomeHub.auth.exception.UnauthorizedException;
import com.ga.HomeHub.auth.model.EmailVerificationToken;
import com.ga.HomeHub.auth.model.PasswordResetToken;
import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.Role;
import com.ga.HomeHub.auth.model.enums.UserStatus;
import com.ga.HomeHub.auth.repository.EmailVerificationTokenRepository;
import com.ga.HomeHub.auth.repository.PasswordResetTokenRepository;
import com.ga.HomeHub.auth.repository.UserRepository;
import com.ga.HomeHub.auth.security.JWTUtils;
import com.ga.HomeHub.auth.security.MyUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final UserRepository users;
    private final EmailVerificationTokenRepository verifyRepository;
    private final PasswordResetTokenRepository resetRepository;
    private final PasswordEncoder encoder;
    private final AuthenticationManager auth;
    private final EmailService email;
    private final JWTUtils jwt;

    private final Logger logger = Logger.getLogger(AuthService.class.getName());

    @Value("${app.base-url}")
    private String baseUrl;

    //Creates the account, verification token and email outbox record in one transaction.
    public void registerUser(RegisterRequest request){
        if(users.existsByEmailAddress(request.email().toLowerCase(Locale.ROOT))){
            throw new InformationExistException("Email already registered");
        }
        if(request.role() == Role.ADMIN){
            throw new UnauthorizedException("Admin account can not self-register");
        }
        User user = new User();
        user.setFirstName(request.firstname());
        user.setLastName(request.lastName());
        user.setEmailAddress(request.email().toLowerCase(Locale.ROOT));
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

    //Consumes the locked verification token and verifies the account in one transaction.
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = verifyRepository.findByToken(token).orElseThrow(() -> new InformationNotFoundException("Invalid verification token"));

        if(verificationToken.getUsedAt() != null){
            throw new InformationExistException("Email already verified");
        }
        //A token is invalid at or after its expiry time.
        if(!verificationToken.getExpiredAt().isAfter(LocalDateTime.now())){
            throw new UnauthorizedException("Verification token expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        users.save(user);
        verificationToken.setUsedAt(LocalDateTime.now());
        verifyRepository.save(verificationToken);
    }

    //Authenticates an active, verified account and issues its JWT.
    public LoginResponse loginUser(LoginRequest request){
        User user = users.findUserByEmailAddress(request.email().toLowerCase(Locale.ROOT));
        if(user == null){
            logger.log(Level.WARNING,"Login failed: invalid credentials");
            throw new AuthenticationFailedException("Invalid credentials");
        }
        if(user.getStatus() != UserStatus.ACTIVE){
            logger.log(Level.WARNING,"Login failed: Inactive Account");
            throw new UnauthorizedException("Inactive Account");
        }
        if(!user.isEmailVerified()){
            logger.log(Level.WARNING,"Login failed: email not verify");
            throw new UnauthorizedException("Verify email before login");
        }
        try{
            auth.authenticate(new UsernamePasswordAuthenticationToken(request.email().toLowerCase(Locale.ROOT), request.password()));
        } catch (AuthenticationException e){
            logger.log(Level.WARNING,"Failed login: Invalid credentials");
            throw new AuthenticationFailedException("Invalid credentials");
        }
        logger.log(Level.INFO,"Login success");
        MyUserDetails userDetails = new MyUserDetails(user);
        return new LoginResponse(jwt.generateJwtToken(userDetails));
    }

    //Creates a reset token and email outbox record without revealing unknown accounts.
    public void requestPasswordReset(String address) {
        User user = users.findUserByEmailAddress(address.toLowerCase(Locale.ROOT));
        if (user != null) {
            PasswordResetToken token = new PasswordResetToken();

            token.setUser(user);
            token.setToken(UUID.randomUUID().toString());
            token.setExpiredAt(LocalDateTime.now().plusMinutes(30));

            resetRepository.save(token);

            email.sendEmail(user.getEmailAddress(), "HomeHub password reset", "Reset token: " + token.getToken());
        }
    }


    //Updates the password, consumes the locked token and records the email in one transaction.
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken t = resetRepository.findByToken(request.token()).orElseThrow(() -> new InformationNotFoundException("Reset token not found"));

        //Reject consumed tokens and tokens at or after their expiry time.
        if (t.getUsedAt() != null || !t.getExpiredAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token is invalid or expired");
        }

        User user = t.getUser();
        user.setPasswordHash(encoder.encode(request.newPassword()));
        users.save(user);

        t.markUsed();
        resetRepository.save(t);

        email.sendEmail(user.getEmailAddress(), "HomeHub - Password Changed", "Hello " + user.getFirstName() + ",\n\n" + "Your HomeHub password has been changed successfully.\n\n" + "HomeHub Team");
    }
}