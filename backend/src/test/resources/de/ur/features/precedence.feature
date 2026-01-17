Feature: Precedence Constraint Behavior
  "B must be preceded by A"

  Scenario: Starts Init and Fulfills if A then B
    Given a "Precedence" constraint named "prec1" defining "B" must be preceded by "A"
    And the constraint "prec1" is in "INIT" state
    When event "A" occurs
    Then the status of "prec1" should be "INIT"
    When event "B" occurs
    Then the status of "prec1" should be "FULFILLED"

  Scenario: Violates Temporarily then Permanently if B without A
    Given a "Precedence" constraint named "prec2" defining "B" must be preceded by "A"
    When event "B" occurs
    Then the status of "prec2" should be "TEMPORARY_VIOLATION"
    When 2 seconds pass
    Then the status of "prec2" should be "PERMANENT_VIOLATION"
