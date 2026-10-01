package com.ga.HomeHub.service;

import com.ga.HomeHub.repository.EmailVerificationTokenRepository;
import com.ga.HomeHub.repository.PasswordResetTokenRepository;
import com.ga.HomeHub.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
public static void main(String[] args) {

}



    /*
    public void registerUser()
    public void verifyEmail()
    public LoginResponse loginUser()
    public void requestPasswordReset()
    public void resetPassword()
    * */
}
