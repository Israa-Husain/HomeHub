package com.ga.auth.cucumber;

import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.UserStatus;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthSteps {
    private final ScenarioState state;
    private final AuthTestData data;

    public AuthSteps(ScenarioState state, AuthTestData data) {
        this.state = state;
        this.data = data;
    }

    @Given("a new registration email")
    public void newEmail() {
        state.setEmail(data.email());
    }

    @Given("an active verified homeowner")
    public void verified() {
        newEmail();
        data.account(state.getEmail(), true, UserStatus.ACTIVE);
    }

    @Given("an active unverified homeowner")
    public void unverified() {
        newEmail();
        data.account(state.getEmail(), false, UserStatus.ACTIVE);
    }

    @Given("an inactive verified homeowner")
    public void inactive() {
        newEmail();
        data.account(state.getEmail(), true, UserStatus.INACTIVE);
    }

    @Given("the homeowner is logged in")
    public void loggedIn() {
        login();
        assertEquals(200, state.getResponse().statusCode(), "Fixture login failed");
        state.setToken(state.getResponse().jsonPath().getString("token"));
    }

    @When("I register as {string}")
    public void register(String role) {
        registerWith(role, AuthTestData.PASSWORD);
    }

    @When("I register with a short password")
    public void shortPassword() {
        registerWith("HOMEOWNER", "short");
    }

    private void registerWith(String role, String password) {
        state.setResponse(state.request().contentType(ContentType.JSON).body(Map.of(
                        "firstname", "Cucumber", "lastName", "Account", "email", state.getEmail(),
                        "password", password, "phoneNumber", "33331234", "role", role))
                .post("/api/auth/register"));
    }

    @When("I log in with the correct password")
    public void login() {
        loginWith(AuthTestData.PASSWORD);
    }

    @When("I log in with the wrong password")
    public void wrongPassword() {
        loginWith("WrongPassword123!");
    }

    @When("I log in with the new password")
    public void newPassword() {
        loginWith(AuthTestData.NEW_PASSWORD);
    }

    private void loginWith(String password) {
        state.setResponse(state.request().contentType(ContentType.JSON)
                .body(Map.of("email", state.getEmail(), "password", password)).post("/api/auth/login"));
    }

    @When("I send an invalid login email")
    public void invalidLogin() {
        state.setResponse(state.request().contentType(ContentType.JSON)
                .body(Map.of("email", "invalid", "password", AuthTestData.PASSWORD)).post("/api/auth/login"));
    }

    @When("I request my profile")
    public void profile() {
        state.setResponse(state.request().get("/api/users/profile"));
    }

    @When("I request my profile without a token")
    public void anonymousProfile() {
        state.setToken(null);
        profile();
    }

    @When("I request my profile with an invalid token")
    public void invalidToken() {
        state.setToken("invalid-token");
        profile();
    }

    @When("the account is deactivated")
    public void deactivate() {
        data.deactivate(state.getEmail());
    }

    @When("I verify the registration token")
    public void verify() {
        if (state.getVerificationToken() == null) {
            state.setVerificationToken(data.verificationToken(state.getEmail()));
        }
        state.setResponse(state.request().queryParam("token", state.getVerificationToken()).get("/api/auth/verify"));
    }

    @When("the registration token expires")
    public void expireVerification() {
        state.setVerificationToken(data.verificationToken(state.getEmail()));
        data.expireVerification(state.getVerificationToken());
    }

    @When("I verify an unknown token")
    public void unknownVerification() {
        state.setResponse(state.request().queryParam("token", UUID.randomUUID().toString()).get("/api/auth/verify"));
    }

    @When("I request a password reset")
    public void forgot() {
        state.setResponse(state.request().contentType(ContentType.JSON)
                .body(Map.of("email", state.getEmail())).post("/api/auth/forgot-password"));
    }

    @When("I reset the password with the issued token")
    public void reset() {
        if (state.getResetToken() == null) {
            state.setResetToken(data.resetToken(state.getEmail()));
        }
        resetWith(state.getResetToken());
    }

    @When("the reset token expires")
    public void expireReset() {
        state.setResetToken(data.resetToken(state.getEmail()));
        data.expireReset(state.getResetToken());
    }

    @When("I reset the password with an unknown token")
    public void unknownReset() {
        resetWith(UUID.randomUUID().toString());
    }

    private void resetWith(String token) {
        state.setResponse(state.request().contentType(ContentType.JSON)
                .body(Map.of("token", token, "newPassword", AuthTestData.NEW_PASSWORD)).post("/api/auth/reset-password"));
    }

    @When("I request current account metadata")
    public void current() {
        state.setResponse(state.request().get("/internal/users/current"));
    }

    @When("I request account metadata without an internal key")
    public void noInternalKey() {
        state.setResponse(state.request().get("/internal/users/" + data.user(state.getEmail()).getId()));
    }

    @When("I request account metadata with the internal key")
    public void internalKey() {
        state.setResponse(state.request().header("X-Internal-Key", "cucumber-only-internal-key-1234567890")
                .get("/internal/users/" + data.user(state.getEmail()).getId()));
    }

    @When("I submit eleven login requests in the same rate-limit window")
    public void rateLimit() {
        for (int i = 0; i < 11; i++) {
            wrongPassword();
        }
    }

    @Then("the response status is {int}")
    public void status(int expected) {
        assertEquals(expected, state.getResponse().statusCode(), "Unexpected HTTP status; inspect local application logs");
    }

    @Then("I receive a JWT")
    public void jwt() {
        String token = state.getResponse().jsonPath().getString("token");
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
        assertEquals("Bearer", state.getResponse().jsonPath().getString("type"));
    }

    @Then("I do not receive a JWT")
    public void noJwt() {
        assertNull(state.getResponse().jsonPath().getString("token"));
    }

    @Then("the returned account belongs to the homeowner")
    public void owner() {
        assertEquals(state.getEmail(), state.getResponse().jsonPath().getString("email"));
        assertEquals(data.user(state.getEmail()).getId().longValue(), state.getResponse().jsonPath().getLong("id"));
        assertNull(state.getResponse().jsonPath().getString("passwordHash"));
    }

    @Then("the account is stored as unverified {string}")
    public void stored(String role) {
        User user = data.user(state.getEmail());
        assertNotNull(user);
        assertFalse(user.isEmailVerified());
        assertEquals(role, user.getRole().name());
        assertTrue(data.matchesPassword(state.getEmail(), AuthTestData.PASSWORD));
        assertFalse(AuthTestData.PASSWORD.equals(user.getPasswordHash()));
        assertEquals(1, data.count("email_verification_token"));
    }

    @Then("the account is verified and the verification token is consumed")
    public void verifiedToken() {
        assertTrue(data.user(state.getEmail()).isEmailVerified());
        assertTrue(data.consumed("email_verification_token", state.getVerificationToken()));
    }

    @Then("the account remains unverified")
    public void remainsUnverified() {
        assertFalse(data.user(state.getEmail()).isEmailVerified());
    }

    @Then("no account or verification token is created")
    public void noAccount() {
        assertEquals(0, data.count("users"));
        assertEquals(0, data.count("email_verification_token"));
    }

    @Then("there is still one account")
    public void oneAccount() {
        assertEquals(1, data.count("users"));
    }

    @Then("there are {int} pending email events")
    public void events(int expected) {
        var events = data.events();
        assertEquals(expected, events.size());
        for (var row : events) {
            assertNull(row.get("PUBLISHED_AT"));
            Map<String, Object> payload = data.payload(row.get("PAYLOAD"));
            assertEquals("EMAIL", payload.get("eventType"));
            assertEquals(1, payload.get("schemaVersion"));
            assertEquals(state.getEmail(), payload.get("email"));
            assertNotNull(payload.get("eventId"));
        }
    }

    @Then("one reset token is created")
    public void oneReset() {
        assertEquals(1, data.count("password_reset_token"));
    }

    @Then("no reset token is created")
    public void noReset() {
        assertEquals(0, data.count("password_reset_token"));
    }

    @Then("the reset token is consumed and the new password is stored")
    public void resetStored() {
        assertTrue(data.consumed("password_reset_token", state.getResetToken()));
        assertTrue(data.matchesPassword(state.getEmail(), AuthTestData.NEW_PASSWORD));
    }

    @Then("the original password remains unchanged")
    public void unchanged() {
        assertTrue(data.matchesPassword(state.getEmail(), AuthTestData.PASSWORD));
    }
}
