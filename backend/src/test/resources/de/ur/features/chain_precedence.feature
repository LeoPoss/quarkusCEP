Feature: Chain Precedence Constraint Behavior
  "B must be immediately preceded by A"

  # Scenario disabled as corresponding unit test is @Disabled (Fulfillment query issue)
  # Scenario: A then B is Fulfilled
  #   Given a "Chain Precedence" constraint named "cp1" defining "B" must be immediately preceded by "A"
  #   When event "A" occurs
  #   Then the status of "cp1" should be "INIT"
  #   When event "B" occurs
  #   Then the status of "cp1" should be "FULFILLED"

  Scenario: A then C then B violates
    Given a "Chain Precedence" constraint named "cp2" defining "B" must be immediately preceded by "A"
    When event "A" occurs
    When event "C" occurs
    When event "B" occurs
    Then the status of "cp2" should be "PERMANENT_VIOLATION"
