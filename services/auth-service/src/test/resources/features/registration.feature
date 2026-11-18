Feature: HomeHub registration and email verification
  New accounts require verification before login.

  Scenario Outline: Register an allowed role
    Given a new registration email
    When I register as "<role>"
    Then the response status is 201
    And the account is stored as unverified "<role>"
    And there are 1 pending email events
    Examples:
      | role      |
      | HOMEOWNER |
      | PROVIDER  |

  Scenario: Public registration cannot create an administrator
    Given a new registration email
    When I register as "ADMIN"
    Then the response status is 403
    And no account or verification token is created
    And there are 0 pending email events

  Scenario: Duplicate email is rejected
    Given an active verified homeowner
    When I register as "HOMEOWNER"
    Then the response status is 409
    And there is still one account
    And there are 0 pending email events

  Scenario: A short registration password is rejected
    Given a new registration email
    When I register with a short password
    Then the response status is 400
    And no account or verification token is created
    And there are 0 pending email events

  Scenario: Verification enables login and consumes its token
    Given a new registration email
    When I register as "HOMEOWNER"
    Then the response status is 201
    When I verify the registration token
    Then the response status is 200
    And the account is verified and the verification token is consumed
    When I log in with the correct password
    Then the response status is 200
    And I receive a JWT

  Scenario: A verification token cannot be reused
    Given a new registration email
    When I register as "HOMEOWNER"
    And I verify the registration token
    Then the response status is 200
    When I verify the registration token
    Then the response status is 409

  Scenario: An expired verification token is rejected
    Given a new registration email
    When I register as "HOMEOWNER"
    And the registration token expires
    And I verify the registration token
    Then the response status is 403
    And the account remains unverified

  Scenario: An unknown verification token is rejected
    When I verify an unknown token
    Then the response status is 404
