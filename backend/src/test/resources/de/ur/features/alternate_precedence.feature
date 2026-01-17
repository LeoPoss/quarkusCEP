Feature: Alternate Precedence Constraint Behavior
  "B must be alternately preceded by A" (A-B-A-B is OK, A-B-B is Violation)

  Scenario: A then B is Fulfilled
    Given a "Alternate Precedence" constraint named "ap1" defining "B" must be alternately preceded by "A"
    When event "A" occurs
    Then the status of "ap1" should be "INIT"
    When event "B" occurs
    Then the status of "ap1" should be "FULFILLED"

  Scenario: B without A violates
    Given a "Alternate Precedence" constraint named "ap2" defining "B" must be alternately preceded by "A"
    When event "B" occurs
    Then the status of "ap2" should be "PERMANENT_VIOLATION"

  Scenario: A then B then B violates
    Given a "Alternate Precedence" constraint named "ap3" defining "B" must be alternately preceded by "A"
    When event "A" occurs
    Then the status of "ap3" should be "INIT"
    When event "B" occurs
    Then the status of "ap3" should be "FULFILLED"
    When event "B" occurs
    Then the status of "ap3" should be "PERMANENT_VIOLATION"
