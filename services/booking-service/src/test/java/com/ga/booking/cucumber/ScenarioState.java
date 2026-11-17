package com.ga.booking.cucumber;

import io.cucumber.spring.ScenarioScope;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@ScenarioScope
@Getter
@Setter
public class ScenarioState{
    private int port;
    private Response response;
    private String actor="homeowner";
    private String token;

    private LocalDate date;
    private LocalTime start=LocalTime.of(9,0);
    private String bookingType="NORMAL";

    private Long homeId;
    private Long otherHomeId;
    private Long serviceId;
    private Long providerId;
    private Long categoryId;

    public RequestSpecification request(){
        RequestSpecification request=RestAssured.given()
                .baseUri("http://localhost")
                .port(port);

        if(token!=null){
            request.header("Authorization","Bearer "+token);
        }

        return request;
    }
}