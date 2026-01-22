Feature: Alternate Precedence Constraint Behavior
  "CloseTicket must be alternately preceded by OpenTicket"

  Scenario: Fulfills if OpenTicket occurs before CloseTicket, and pairs are alternate
    Given a "Alternate Precedence" constraint named "altPrec1" defining "CloseTicket" must be alternately preceded by "OpenTicket"
    When event "OpenTicket" occurs
    # Status remains INIT
    When event "CloseTicket" occurs
    Then the status of "altPrec1" should be "FULFILLED"

  Scenario: Violates if CloseTicket occurs without OpenTicket
    Given a "Alternate Precedence" constraint named "altPrec2" defining "CloseTicket" must be alternately preceded by "OpenTicket"
    When event "CloseTicket" occurs
    Then the status of "altPrec2" should be "PERMANENT_VIOLATION"

  Scenario: Violates if two CloseTickets occur with only one OpenTicket
    Given a "Alternate Precedence" constraint named "altPrec3" defining "CloseTicket" must be alternately preceded by "OpenTicket"
    When event "OpenTicket" occurs
    # Status remains INIT
    When event "CloseTicket" occurs
    Then the status of "altPrec3" should be "FULFILLED"
    When event "CloseTicket" occurs
    Then the status of "altPrec3" should be "PERMANENT_VIOLATION"

  Scenario: Interleaved irrelevant events don't break alternate precedence
    Given a "Alternate Precedence" constraint named "altPrec_irrelevant" defining "CloseTicket" must be alternately preceded by "OpenTicket"
    When event "OpenTicket" occurs
    When event "IrrelevantEvent" occurs
    When event "CloseTicket" occurs
    Then the status of "altPrec_irrelevant" should be "FULFILLED"

  Scenario: Duplicate activation (Open, Open, Close) is valid/invalid depending on logic
    Given a "Alternate Precedence" constraint named "altPrec_dupAct" defining "CloseTicket" must be alternately preceded by "OpenTicket"
    When event "OpenTicket" occurs
    When event "OpenTicket" occurs
    When event "CloseTicket" occurs
    Then the status of "altPrec_dupAct" should be "FULFILLED"
