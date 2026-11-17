Feature: HomeHub login and protected account access

  Scenario: An active verified homeowner can log in
    Given an active verified homeowner
    When I log in with the correct password
    Then the response status is 200
    And I receive a JWT

  Scenario: Incorrect credentials do not return a token
    Given an active verified homeowner
    When I log in with the wrong password
    Then the response status is 401
    And I do not receive a JWT

  Scenario: An unknown account cannot log in
    Given a new registration email
    When I log in with the correct password
    Then the response status is 401
    And I do not receive a JWT

  Scenario: An unverified account cannot log in
    Given an active unverified homeowner
    When I log in with the correct password
    Then the response status is 403
    And I do not receive a JWT

  Scenario: An inactive account cannot log in
    Given an inactive verified homeowner
    When I log in with the correct password
    Then the response status is 403
    And I do not receive a JWT

  Scenario: Login requires a valid email address
    When I send an invalid login email
    Then the response status is 400

  Scenario: A bearer token allows access to the owner's profile
    Given an active verified homeowner
    And the homeowner is logged in
    When I request my profile
    Then the response status is 200
    And the returned account belongs to the homeowner

  Scenario: Profile access requires authentication
    When I request my profile without a token
    Then the response status is 401

  Scenario: An invalid token cannot access a profile
    When I request my profile with an invalid token
    Then the response status is 401

  Scenario: Deactivation blocks an already issued token
    Given an active verified homeowner
    And the homeowner is logged in
    When the account is deactivated
    And I request my profile
    Then the response status is 401

  Scenario: Repeated login attempts are rate limited
    Given an active verified homeowner
    When I submit eleven login requests in the same rate-limit window
    Then the response status is 429
