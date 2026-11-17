package com.ga.auth.cucumber;

import io.cucumber.spring.ScenarioScope;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.Getter;
import lombok.Setter;

@ScenarioScope
@Getter
@Setter
public class ScenarioState {
    private int port;
    private String token;
    private String email;
    private String verificationToken;
    private String resetToken;
    private Response response;

    public RequestSpecification request() {
        RequestSpecification request = RestAssured.given().baseUri("http://localhost").port(port);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }
}
