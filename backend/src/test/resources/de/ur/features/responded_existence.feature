Feature: Responded Existence Constraint Behavior
  "If A occurs, B must occur (before or after)"

  Scenario: A before B fulfills
    Given a "Responded Existence" constraint named "re1" defining "A" must be responded to by "B"
    When event "A" occurs
    Then the status of "re1" should be "TEMPORARY_VIOLATION"
    When event "B" occurs
    Then the status of "re1" should be "FULFILLED"

  Scenario: B before A fulfills immediately
    Given a "Responded Existence" constraint named "re2" defining "A" must be responded to by "B"
    When event "B" occurs
    Then the status of "re2" should be "INIT"
    When event "A" occurs
    Then the status of "re2" should be "FULFILLED"
    # Logic check: If B occurs first, it's satisfied for potential future A. But does it track "future A"? 
    # Usually Responded Existence: Global check. If A exists, B must exist.
    # If B exists first, then when A comes, it is already satisfied.
