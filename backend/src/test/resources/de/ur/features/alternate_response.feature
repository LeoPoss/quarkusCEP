Feature: Alternate Response Constraint Behavior
  "Login must be alternately followed by Logout"

  Scenario: Fulfills if Login then Logout without intervening Login
    Given a "Alternate Response" constraint named "altResp1" defining "Login" must be alternately followed by "Logout"
    When event "Login" occurs
    Then the status of "altResp1" should be "TEMPORARY_VIOLATION"
    When event "Logout" occurs
    Then the status of "altResp1" should be "FULFILLED"

  Scenario: Start violation if Login occurs again before Logout
    Given a "Alternate Response" constraint named "altResp2" defining "Login" must be alternately followed by "Logout"
    When event "Login" occurs
    Then the status of "altResp2" should be "TEMPORARY_VIOLATION"
    When event "Login" occurs
    Then the status of "altResp2" should be "PERMANENT_VIOLATION"

  Scenario: Violation by duplicate target
    Given a "Alternate Response" constraint named "altResp_dupTarget" defining "Login" must be alternately followed by "Logout"
    When event "Login" occurs
    And event "Logout" occurs
    Then the status of "altResp_dupTarget" should be "FULFILLED"
    When event "Logout" occurs
    Then the status of "altResp_dupTarget" should be "FULFILLED"

  Scenario: Interleaved irrelevant events don't affect status
    Given a "Alternate Response" constraint named "altResp_irrelevant" defining "Login" must be alternately followed by "Logout"
    When event "Login" occurs
    Then the status of "altResp_irrelevant" should be "TEMPORARY_VIOLATION"
    When event "IrrelevantEvent" occurs
    Then the status of "altResp_irrelevant" should be "TEMPORARY_VIOLATION"
    When event "Logout" occurs
    Then the status of "altResp_irrelevant" should be "FULFILLED"
