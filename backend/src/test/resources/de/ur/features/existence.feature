Feature: Existence Constraint Behavior
  "SystemInitialization must occur at least once"

  Scenario: Violates initially, fulfills when SystemInitialization occurs
    Given an "Existence" constraint named "exist1" checking for "SystemInitialization"
    Then the status of "exist1" should be "TEMPORARY_VIOLATION"
    When event "SystemInitialization" occurs
    Then the status of "exist1" should be "FULFILLED"
