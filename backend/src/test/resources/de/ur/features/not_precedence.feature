Feature: Not Precedence Constraint Behavior
   "B must not be preceded by A"

  Scenario: Violates if A then B
    Given a "Not Precedence" constraint named "nprec1" defining "B" must not be preceded by "A"
    When event "A" occurs
    When event "B" occurs
    Then the status of "nprec1" should be "PERMANENT_VIOLATION"
