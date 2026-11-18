Feature: Internal account metadata for other microservices

  Scenario: A live bearer token resolves the current account
    Given an active verified homeowner
    And the homeowner is logged in
    When I request current account metadata
    Then the response status is 200
    And the returned account belongs to the homeowner

  Scenario: Current account resolution requires a bearer token
    When I request current account metadata
    Then the response status is 401

  Scenario: Account lookup by ID requires the internal credential
    Given an active verified homeowner
    When I request account metadata without an internal key
    Then the response status is 401

  Scenario: The internal credential permits account lookup by ID
    Given an active verified homeowner
    When I request account metadata with the internal key
    Then the response status is 200
    And the returned account belongs to the homeowner
