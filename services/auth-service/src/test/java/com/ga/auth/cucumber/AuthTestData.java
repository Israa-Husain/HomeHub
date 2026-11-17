package com.ga.auth.cucumber;

import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.Role;
import com.ga.HomeHub.auth.model.enums.UserStatus;
import com.ga.HomeHub.auth.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

public class AuthTestData {
    public static final String PASSWORD = "CucumberPass123!";
    public static final String NEW_PASSWORD = "ChangedPass456!";
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final DataSource source;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthTestData(UserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc, DataSource source) {
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.source = source;
    }

    public String email() {
        return "cucumber-" + UUID.randomUUID() + "@homehub.example";
    }

    //Never allow test cleanup against PostgreSQL or an externally configured database.
    public void clean() {
        try (Connection connection = source.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:homehub-auth-cucumber")) {
                throw new IllegalStateException("Cucumber requires its isolated in-memory H2 database");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot validate test database", e);
        }
        for (String table : List.of("audit_log", "password_reset_token", "email_verification_token", "outbox_event", "users")) {
            jdbc.update("delete from " + table);
        }
    }

    public void account(String email, boolean verified, UserStatus status) {
        User user = new User();
        user.setFirstName("Cucumber");
        user.setLastName("Homeowner");
        user.setEmailAddress(email);
        user.setPhoneNumber("33331234");
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setRole(Role.HOMEOWNER);
        user.setStatus(status);
        user.setEmailVerified(verified);
        users.saveAndFlush(user);
    }

    public User user(String email) {
        return users.findUserByEmailAddress(email);
    }

    public void deactivate(String email) {
        User user = user(email);
        user.setStatus(UserStatus.INACTIVE);
        users.saveAndFlush(user);
    }

    public String verificationToken(String email) {
        return token("email_verification_token", email);
    }

    public String resetToken(String email) {
        return token("password_reset_token", email);
    }

    private String token(String table, String email) {
        return jdbc.queryForObject("select t.token from " + table + " t join users u on u.id=t.user_id where u.email_address=? order by t.id desc limit 1", String.class, email);
    }

    public void expireVerification(String token) {
        expire("email_verification_token", token);
    }

    public void expireReset(String token) {
        expire("password_reset_token", token);
    }

    private void expire(String table, String token) {
        jdbc.update("update " + table + " set expired_at=? where token=?", LocalDateTime.now().minusMinutes(1), token);
    }

    public boolean consumed(String table, String token) {
        return jdbc.queryForObject("select used_at is not null from " + table + " where token=?", Boolean.class, token);
    }

    public long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    public List<Map<String, Object>> events() {
        return jdbc.queryForList("select payload,published_at from outbox_event order by created_at,id");
    }

    public Map<String, Object> payload(Object json) {
        try {
            return mapper.readValue(json.toString(), new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid outbox JSON", e);
        }
    }

    public boolean matchesPassword(String email, String password) {
        return encoder.matches(password, user(email).getPasswordHash());
    }
}
