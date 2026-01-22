Feature: Not Precedence Constraint Behavior
  "EmergencyShutdown must not be preceded by NormalShutdownSequence"

  Scenario: Fulfills if EmergencyShutdown occurs providing NormalShutdownSequence has NOT occurred
    Given a "Not Precedence" constraint named "notPrec1" defining "EmergencyShutdown" must not be preceded by "NormalShutdownSequence"
    When event "EmergencyShutdown" occurs
    Then the status of "notPrec1" should be "FULFILLED"

  Scenario: Violates if EmergencyShutdown occurs AFTER NormalShutdownSequence
    Given a "Not Precedence" constraint named "notPrec2" defining "EmergencyShutdown" must not be preceded by "NormalShutdownSequence"
    When event "NormalShutdownSequence" occurs
    When event "EmergencyShutdown" occurs
    Then the status of "notPrec2" should be "PERMANENT_VIOLATION"

  Scenario: Interleaved irrelevant events don't break Not Precedence
    Given a "Not Precedence" constraint named "notPrec_irrelevant" defining "EmergencyShutdown" must not be preceded by "NormalShutdownSequence"
    When event "IrrelevantEvent" occurs
    When event "EmergencyShutdown" occurs
    Then the status of "notPrec_irrelevant" should be "FULFILLED"
