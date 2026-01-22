Feature: Not Existence Constraint Behavior
  "UnauthorizedDatabaseAccess must never occur"

  Scenario: Fulfills initially, violates when UnauthorizedDatabaseAccess occurs
    Given a "Not Existence" constraint named "notExist1" ensuring "UnauthorizedDatabaseAccess" never occurs
    Then the status of "notExist1" should be "INIT"
    When event "UnauthorizedDatabaseAccess" occurs
    Then the status of "notExist1" should be "PERMANENT_VIOLATION"

  Scenario: Interleaved irrelevant events don't affect Not Existence
    Given a "Not Existence" constraint named "notExist_irrelevant" ensuring "UnauthorizedDatabaseAccess" never occurs
    When event "IrrelevantEvent" occurs
    Then the status of "notExist_irrelevant" should be "INIT"
    When event "UnauthorizedDatabaseAccess" occurs
    Then the status of "notExist_irrelevant" should be "PERMANENT_VIOLATION"
