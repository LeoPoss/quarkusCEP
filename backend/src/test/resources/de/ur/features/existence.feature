Feature: Existence Constraint Behavior
  "A must occur eventually"

  Scenario: Starts Violated and Fulfills on Target
    Given an "Existence" constraint named "ex1" checking for "A"
    And the constraint "ex1" is in "TEMPORARY_VIOLATION" state
    When event "A" occurs
    Then the status of "ex1" should be "FULFILLED"

  Scenario: Unrelated events do not affect status
    Given an "Existence" constraint named "ex2" checking for "A"
    When event "B" occurs
    Then the status of "ex2" should be "TEMPORARY_VIOLATION"
