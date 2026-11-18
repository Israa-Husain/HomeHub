package com.ga.auth;

import com.ga.HomeHub.auth.dto.auth.LoginRequest;
import com.ga.HomeHub.auth.dto.auth.RegisterRequest;
import com.ga.HomeHub.auth.exception.InformationExistException;
import com.ga.HomeHub.auth.exception.UnauthorizedException;
import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.Role;
import com.ga.HomeHub.auth.model.enums.UserStatus;
import com.ga.HomeHub.auth.repository.UserRepository;
import com.ga.HomeHub.auth.service.AuthService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationRegistrationTest{
    @Mock private UserRepository userRepository;
    @InjectMocks private AuthService authService;

    @Test
    @DisplayName("Login fails when email is not verified")
    void loginFailsWhenEmailIsNotVerified(){
        User user=new User();
        user.setEmailAddress("user@gmail.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        when(userRepository.findUserByEmailAddress("user@gmail.com")).thenReturn(user);

        LoginRequest request=new LoginRequest("user@gmail.com","Password123");

        assertThrows(UnauthorizedException.class,()->authService.loginUser(request));
    }

    @Test
    @DisplayName("Inactive user cannot login")
    void inactiveUserCannotLogin(){
        User user=new User();
        user.setEmailAddress("user@gmail.com");
        user.setStatus(UserStatus.INACTIVE);
        user.setEmailVerified(true);

        when(userRepository.findUserByEmailAddress("user@gmail.com")).thenReturn(user);

        LoginRequest request=new LoginRequest("user@gmail.com","Password123");

        assertThrows(UnauthorizedException.class,()->authService.loginUser(request));
    }

    @Test
    @DisplayName("Registration rejects an invalid email")
    void registerRequestRejectsInvalidEmail(){
        RegisterRequest request=new RegisterRequest("Sara","Ahmed","not-an-email","Password123","33333333",Role.HOMEOWNER);

        try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
            Validator validator=factory.getValidator();
            Set<ConstraintViolation<RegisterRequest>> errors=validator.validate(request);

            assertFalse(errors.isEmpty());
        }
    }

    @Test
    @DisplayName("Registration rejects a password shorter than 8 characters")
    void registerRequestRejectsShortPassword(){
        RegisterRequest request=new RegisterRequest("Sara","Ahmed","sara@gmail.com","123","33333333",Role.HOMEOWNER);

        try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
            Validator validator=factory.getValidator();
            Set<ConstraintViolation<RegisterRequest>> errors=validator.validate(request);

            assertFalse(errors.isEmpty());
        }
    }

    @Test
    @DisplayName("Duplicate email cannot be registered")
    void duplicateEmailCannotRegister(){
        RegisterRequest request=new RegisterRequest("Sara","Ahmed","sara@gmail.com","Password123","33333333",Role.HOMEOWNER);

        when(userRepository.existsByEmailAddress("sara@gmail.com")).thenReturn(true);

        assertThrows(InformationExistException.class,()->authService.registerUser(request));
    }

    @Test
    @DisplayName("Admin cannot self-register")
    void adminCannotSelfRegister(){
        RegisterRequest request=new RegisterRequest("Admin","User","admin@gmail.com","Password123","33333333",Role.ADMIN);

        when(userRepository.existsByEmailAddress("admin@gmail.com")).thenReturn(false);

        assertThrows(UnauthorizedException.class,()->authService.registerUser(request));
    }
}
