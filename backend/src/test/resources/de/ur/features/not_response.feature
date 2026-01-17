Feature: Not Response Constraint Behavior
  "A must not be followed by B"

  Scenario: Violates if A then B
    Given a "Not Response" constraint named "nresp1" defining "A" must not be followed by "B"
    When event "A" occurs
    When event "B" occurs
    Then the status of "nresp1" should be "PERMANENT_VIOLATION"
