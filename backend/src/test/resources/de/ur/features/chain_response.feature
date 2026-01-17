Feature: Chain Response Constraint Behavior
  "A must be immediately followed by B"

  Scenario: Fulfills if A immediately followed by B
    Given a "Chain Response" constraint named "cresp1" defining "A" must be immediately followed by "B"
    When event "A" occurs
    Then the status of "cresp1" should be "TEMPORARY_VIOLATION"
    When event "B" occurs
    Then the status of "cresp1" should be "FULFILLED"
