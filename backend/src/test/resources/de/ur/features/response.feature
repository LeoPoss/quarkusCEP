Feature: Response Constraint Behavior
  "A must be eventually followed by B"

  Scenario: Fulfills if A then B
    Given a "Response" constraint named "resp1" defining "A" must be eventually followed by "B"
    When event "A" occurs
    Then the status of "resp1" should be "TEMPORARY_VIOLATION"
    When event "B" occurs
    Then the status of "resp1" should be "FULFILLED"
