Feature: HomeHub password reset

  Scenario: An existing account receives a pending reset email
    Given an active verified homeowner
    When I request a password reset
    Then the response status is 200
    And one reset token is created
    And there are 1 pending email events

  Scenario: An unknown email receives the same generic acknowledgement
    Given a new registration email
    When I request a password reset
    Then the response status is 200
    And no reset token is created
    And there are 0 pending email events

  Scenario: A valid reset token changes the password
    Given an active verified homeowner
    When I request a password reset
    And I reset the password with the issued token
    Then the response status is 200
    And the reset token is consumed and the new password is stored
    And there are 2 pending email events
    When I log in with the new password
    Then the response status is 200
    And I receive a JWT
    When I log in with the correct password
    Then the response status is 401

  Scenario: A consumed reset token cannot be reused
    Given an active verified homeowner
    When I request a password reset
    And I reset the password with the issued token
    Then the response status is 200
    When I reset the password with the issued token
    Then the response status is 400
    And there are 2 pending email events

  Scenario: An expired reset token cannot change the password
    Given an active verified homeowner
    When I request a password reset
    And the reset token expires
    And I reset the password with the issued token
    Then the response status is 400
    And the original password remains unchanged
    And there are 1 pending email events

  Scenario: An unknown reset token is rejected
    Given an active verified homeowner
    When I reset the password with an unknown token
    Then the response status is 404
    And the original password remains unchanged
    And there are 0 pending email events
