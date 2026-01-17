Feature: Not Existence Constraint Behavior
  "A must never occur"

  Scenario: Violates if Target occurs
    Given a "Not Existence" constraint named "nex1" ensuring "A" never occurs
    Then the status of "nex1" should be "INIT"
    When event "A" occurs
    Then the status of "nex1" should be "PERMANENT_VIOLATION"
