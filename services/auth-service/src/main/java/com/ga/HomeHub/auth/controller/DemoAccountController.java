package com.ga.HomeHub.auth.controller;

import com.ga.HomeHub.auth.dto.user.UserResponse;
import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.Role;
import com.ga.HomeHub.auth.model.enums.UserStatus;
import com.ga.HomeHub.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Demo Accounts",description="Account provisioning for isolated demo environments")
@SecurityRequirement(name="internalKey")
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name="homehub.demo.enabled",havingValue="true")
public class DemoAccountController{
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final Environment environment;

    public record Request(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String email,
            @NotBlank String role,
            @Size(min=12) @NotBlank String password,
            @NotBlank String status,
            boolean verified
    ){}

    @Operation(summary="Provision a fictional demo identity; requires demo profile and service credential")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Demo account created or existing account returned"),
            @ApiResponse(responseCode="400",description="Invalid account data or demo restrictions not satisfied"),
            @ApiResponse(responseCode="401",description="Missing or invalid internal service credential"),
            @ApiResponse(responseCode="403",description="Internal service access denied"),
            @ApiResponse(responseCode="500",description="Unexpected server error")
    })
    @PostMapping("/internal/demo/users")
    @Transactional
    public UserResponse provision(@Valid @RequestBody Request request){
        if(!environment.matchesProfiles("demo")
                || !jdbc.queryForObject("select current_database()",String.class).endsWith("_demo")
                || !request.email().matches("demo[.][a-z0-9]+@homehub[.]example")
                || request.password().length()<12){
            throw new IllegalArgumentException("Demo provisioning is restricted");
        }

        //Serialize demo account creation across application instances.
        jdbc.execute("select pg_advisory_xact_lock(70448423002)");

        User existing=users.findUserByEmailAddress(request.email());
        if(existing!=null){
            return UserResponse.from(existing);
        }

        User user=new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmailAddress(request.email());
        user.setRole(Role.valueOf(request.role()));
        user.setStatus(UserStatus.valueOf(request.status()));
        user.setEmailVerified(request.verified());
        user.setPasswordHash(encoder.encode(request.password()));

        return UserResponse.from(users.saveAndFlush(user));
    }
}