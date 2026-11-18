package com.ga.booking.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookingSteps{
    private final ScenarioState state;
    private final BookingTestData data;

    public BookingSteps(ScenarioState state,BookingTestData data){
        this.state=state;
        this.data=data;
    }

    @Given("a {string} booking exists")
    public void existing(String status){
        data.fixtureBooking(status);
    }

    @Given("the provider has no availability")
    public void noAvailability(){
        data.noAvailability();
    }

    @Given("the requested home is inactive")
    public void inactiveHome(){
        data.changeHome(home->home.setActive(false));
    }

    @Given("the service category is inactive")
    public void inactiveCategory(){
        data.inactiveCategory();
    }

    @Given("the provider is not approved")
    public void unapproved(){
        data.unapprovedProvider();
    }

    @Given("the requested time is blocked off")
    public void timeOff(){
        data.timeOff();
    }

    @When("I create the booking")
    public void create(){
        Map<String,Object> body=new HashMap<>();
        body.put("homeId",state.getHomeId());
        body.put("serviceId",state.getServiceId());
        body.put("bookingDate",state.getDate().toString());
        body.put("startTime",state.getStart().toString());
        body.put("bookingType",state.getBookingType());
        body.put("note","Cucumber fixture booking");

        state.setResponse(
                state.request()
                        .contentType(ContentType.JSON)
                        .body(body)
                        .post("/api/booking")
        );
    }

    @When("I create the booking for the other homeowner's home")
    public void otherHome(){
        state.setHomeId(state.getOtherHomeId());
        create();
    }

    @When("I create a booking in the past")
    public void past(){
        state.setDate(LocalDate.now().minusDays(1));
        create();
    }

    @When("I create a normal booking less than 24 hours away")
    public void shortNotice(){
        state.setDate(LocalDate.now());
        state.setStart(
                LocalTime.now()
                        .plusHours(2)
                        .withSecond(0)
                        .withNano(0)
        );
        create();
    }

    @When("I create an adjacent booking")
    public void adjacent(){
        state.setStart(LocalTime.of(10,0));
        create();
    }

    @When("I request available times")
    public void availableTimes(){
        state.setResponse(
                state.request()
                        .queryParam("serviceId",state.getServiceId())
                        .queryParam("date",state.getDate().toString())
                        .queryParam("bookingType","NORMAL")
                        .get("/api/booking/available-times")
        );
    }

    @Then("the response status is {int}")
    public void status(int expected){
        assertEquals(
                expected,
                state.getResponse().statusCode(),
                "Unexpected status. Inspect local Booking logs"
        );
    }

    @Then("the booking status is {string}")
    public void bookingStatus(String expected){
        assertEquals(
                expected,
                state.getResponse().jsonPath().getString("status")
        );
    }

    @Then("the final price is {double}")
    public void price(double expected){
        assertEquals(
                expected,
                state.getResponse().jsonPath().getDouble("finalPrice"),
                0.001
        );
    }

    @Then("there are {int} saved bookings")
    public void bookings(int count){
        assertEquals(count,data.count("bookings"));
    }

    @Then("there are {int} pending outbox events")
    public void events(int expected){
        var rows=data.events();
        assertEquals(expected,rows.size());

        for(var row:rows){
            assertNull(row.get("PUBLISHED_AT"));

            Map<String,Object> payload=data.payload(
                    row.get("PAYLOAD")
            );

            assertEquals(1,payload.get("schemaVersion"));
            assertNotNull(payload.get("eventId"));

            assertTrue(
                    "EMAIL".equals(payload.get("eventType"))
                            ||"NOTIFICATION".equals(payload.get("eventType"))
            );
        }
    }

    @Then("a booking audit entry exists")
    public void audit(){
        assertTrue(data.count("audit_log")>0);
    }

    @Then("the response deadline is recorded")
    public void deadline(){
        assertNotNull(
                state.getResponse()
                        .jsonPath()
                        .getString("responseDeadline")
        );
    }

    @Then("the occupied appointment is excluded")
    public void excluded(){
        assertFalse(
                state.getResponse()
                        .jsonPath()
                        .getList("",String.class)
                        .contains("09:00:00")
        );
    }
}