Feature: Alternate Response Constraint Behavior
  "A must be alternately followed by B" (A-B-A-B is OK, A-A-B is Violation)

  Scenario: A then B is Fulfilled
    Given a "Alternate Response" constraint named "ar1" defining "A" must be alternately followed by "B"
    When event "A" occurs
    When 2 seconds pass
    Then the status of "ar1" should be "TEMPORARY_VIOLATION"
    When event "B" occurs
    Then the status of "ar1" should be "FULFILLED"

  Scenario: A then A violates Permanently
    Given a "Alternate Response" constraint named "ar2" defining "A" must be alternately followed by "B"
    When event "A" occurs
    When 2 seconds pass
    Then the status of "ar2" should be "TEMPORARY_VIOLATION"
    When event "A" occurs
    Then the status of "ar2" should be "PERMANENT_VIOLATION"
