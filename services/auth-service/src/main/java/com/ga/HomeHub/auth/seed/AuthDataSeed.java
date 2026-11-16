package com.ga.HomeHub.auth.seed;

import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.Role;
import com.ga.HomeHub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@ConditionalOnProperty(name="homehub.seed.enabled",havingValue="true")
@RequiredArgsConstructor
public class AuthDataSeed implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    @Value("${homehub.bootstrap.email:}")
    private String email;

    @Value("${homehub.bootstrap.password:}")
    private String password;

    @Override
    @Transactional
    public void run(String... args) {
        if(email.isBlank() && password.isBlank()){
            return;
        }
        if(email.isBlank() || password.length()<12){
            throw new IllegalArgumentException("Configure an administrator email and password of at least 12 characters");
        }

        //Serialize administrator creation when multiple Auth instances start together.
        jdbc.execute("select pg_advisory_xact_lock(70448423001)");

        if(users.existsByEmailAddress(email.toLowerCase(Locale.ROOT))){
            return;
        }

        //Insert into the existing users table without changing existing accounts.
        User admin = new User();
        admin.setFirstName("HomeHub");
        admin.setLastName("Administrator");
        admin.setEmailAddress(email.toLowerCase(Locale.ROOT));
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);
        users.save(admin);
    }
}