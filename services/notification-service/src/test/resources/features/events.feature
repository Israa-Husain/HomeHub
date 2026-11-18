Feature: Kafka processing and email delivery

  Scenario: Notification event creates history and a published receipt
    Given a "NOTIFICATION" event
    When I publish the event
    And the event is consumed
    Then there are 1 notification rows and 1 receipt rows
    And the receipt is already marked published

  Scenario: Duplicate notification delivery creates one history row
    Given a "NOTIFICATION" event
    When I publish the event
    And the event is consumed
    And I publish the event
    And the event is consumed
    Then there are 1 notification rows and 1 receipt rows

  Scenario: Email event invokes SMTP with the event fields
    Given a "EMAIL" event
    When I publish the event
    And the event is consumed
    Then there are 0 notification rows and 1 receipt rows
    And SMTP was called 1 times
    And SMTP received the event email fields

  Scenario: Duplicate email delivery is deduplicated
    Given a "EMAIL" event
    When I publish the event
    And the event is consumed
    And I publish the event
    And the event is consumed
    Then there are 0 notification rows and 1 receipt rows
    And SMTP was called 1 times

  Scenario: Temporary SMTP failure is retried
    Given a "EMAIL" event
    And SMTP fails once then recovers
    When I publish the event
    And the event is consumed
    Then SMTP was called 2 times
    And there are 0 notification rows and 1 receipt rows

  Scenario: Permanent SMTP failure goes to DLT without a receipt
    Given a "EMAIL" event
    And SMTP keeps failing
    When I publish the event
    Then the event reaches the dead-letter topic
    And SMTP was called 4 times
    And there are 0 notification rows and 0 receipt rows

  Scenario: Malformed JSON goes to DLT
    When I publish malformed JSON
    Then the event reaches the dead-letter topic
    And there are 0 notification rows and 0 receipt rows

  Scenario: Unsupported event version goes to DLT
    Given a "NOTIFICATION" event
    When I use an unsupported schema version
    And I publish the event
    Then the event reaches the dead-letter topic
    And there are 0 notification rows and 0 receipt rows

  Scenario: Failed notification insert rolls back the receipt
    Given a "NOTIFICATION" event
    When I use an unknown notification recipient
    And I publish the event
    Then the event reaches the dead-letter topic
    And there are 0 notification rows and 0 receipt rows

  Scenario: Reusing an event ID for changed content is rejected
    Given a "NOTIFICATION" event
    When I publish the event
    And the event is consumed
    And I reuse the event ID with different content
    Then the event reaches the dead-letter topic
    And there are 1 notification rows and 1 receipt rows
