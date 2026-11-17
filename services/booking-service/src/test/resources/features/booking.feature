Feature: HomeHub normal booking rules

  Scenario: Create a normal booking with sufficient notice
    When I create the booking
    Then the response status is 201
    And the booking status is "PENDING"
    And the final price is 100.0
    And the response deadline is recorded
    And there are 1 saved bookings
    And there are 2 pending outbox events
    And a booking audit entry exists

  Scenario: Reject normal booking without 24 hours notice
    When I create a normal booking less than 24 hours away
    Then the response status is 409
    And there are 0 saved bookings
    And there are 0 pending outbox events

  Scenario: Reject a past booking
    When I create a booking in the past
    Then the response status is 409
    And there are 0 saved bookings

  Scenario: Reject another homeowner home
    When I create the booking for the other homeowner's home
    Then the response status is 403
    And there are 0 saved bookings

  Scenario: Reject a soft-deleted home
    Given the requested home is inactive
    When I create the booking
    Then the response status is 409
    And there are 0 saved bookings

  Scenario: Reject an inactive category
    Given the service category is inactive
    When I create the booking
    Then the response status is 409
    And there are 0 saved bookings

  Scenario: Reject an unapproved provider
    Given the provider is not approved
    When I create the booking
    Then the response status is 409
    And there are 0 saved bookings

  Scenario: Reject booking outside working availability
    Given the provider has no availability
    When I create the booking
    Then the response status is 409
    And there are 0 saved bookings

  Scenario: Reject an overlapping booking
    Given a "PENDING" booking exists
    When I create the booking
    Then the response status is 409
    And there are 1 saved bookings
    And there are 0 pending outbox events

  Scenario: Allow an adjacent appointment
    Given a "CONFIRMED" booking exists
    When I create an adjacent booking
    Then the response status is 201
    And there are 2 saved bookings

  Scenario: Reject a blocked time-off interval
    Given the requested time is blocked off
    When I create the booking
    Then the response status is 409
    And there are 0 saved bookings

  Scenario: Discover times without reserving a slot
    Given a "CONFIRMED" booking exists
    When I request available times
    Then the response status is 200
    And the occupied appointment is excluded
    And there are 1 saved bookings
