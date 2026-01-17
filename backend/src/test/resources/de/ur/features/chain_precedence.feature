Feature: Chain Precedence Constraint Behavior
  "HighSecurityAreaAccess must be immediately preceded by BiometricScanSuccess"

  Scenario: Fulfills if BiometricScanSuccess occurs immediately before HighSecurityAreaAccess
    Given a "Chain Precedence" constraint named "chainPrec1" defining "HighSecurityAreaAccess" must be immediately preceded by "BiometricScanSuccess"
    When event "BiometricScanSuccess" occurs
    # Status remains INIT
    When event "HighSecurityAreaAccess" occurs
    Then the status of "chainPrec1" should be "FULFILLED"

  Scenario: Violates if HighSecurityAreaAccess occurs without immediate BiometricScanSuccess
    Given a "Chain Precedence" constraint named "chainPrec2" defining "HighSecurityAreaAccess" must be immediately preceded by "BiometricScanSuccess"
    When event "HighSecurityAreaAccess" occurs
    Then the status of "chainPrec2" should be "PERMANENT_VIOLATION"

  Scenario: Violates if intermediate event breaks sequence
    Given a "Chain Precedence" constraint named "chainPrec3" defining "HighSecurityAreaAccess" must be immediately preceded by "BiometricScanSuccess"
    When event "BiometricScanSuccess" occurs
    # Status remains INIT
    When event "OtherEvent" occurs
    When event "HighSecurityAreaAccess" occurs
    Then the status of "chainPrec3" should be "PERMANENT_VIOLATION"

  Scenario: Violation by non-immediate predecessor (Order check with Irrelevant event)
    Given a "Chain Precedence" constraint named "chainPrec_irrelevant" defining "HighSecurityAreaAccess" must be immediately preceded by "BiometricScanSuccess"
    When event "BiometricScanSuccess" occurs
    When event "IrrelevantEvent" occurs
    When event "HighSecurityAreaAccess" occurs
    Then the status of "chainPrec_irrelevant" should be "PERMANENT_VIOLATION"

  Scenario: Correct sequence maintains fulfillment
    Given a "Chain Precedence" constraint named "chainPrec_ok" defining "HighSecurityAreaAccess" must be immediately preceded by "BiometricScanSuccess"
    When event "BiometricScanSuccess" occurs
    When event "HighSecurityAreaAccess" occurs
    Then the status of "chainPrec_ok" should be "FULFILLED"
    When event "BiometricScanSuccess" occurs
    When event "HighSecurityAreaAccess" occurs
    Then the status of "chainPrec_ok" should be "TEMPORARY_VIOLATION"



