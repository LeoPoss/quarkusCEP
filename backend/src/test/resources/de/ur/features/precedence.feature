Feature: Precedence Constraint Behavior
  "ShipmentShipped must be preceded by ShipmentPacked"

  Scenario: Fulfills if ShipmentPacked occurs before ShipmentShipped
    Given a "Precedence" constraint named "prec1" defining "ShipmentShipped" must be preceded by "ShipmentPacked"
    When event "ShipmentPacked" occurs
    When event "ShipmentShipped" occurs
    Then the status of "prec1" should be "FULFILLED"

  Scenario: Violates if ShipmentShipped occurs without ShipmentPacked
    Given a "Precedence" constraint named "prec2" defining "ShipmentShipped" must be preceded by "ShipmentPacked"
    When event "ShipmentShipped" occurs
    Then the status of "prec2" should be "PERMANENT_VIOLATION"

  Scenario: Multiple activations satisfied by single target
    Given a "Precedence" constraint named "prec_multi" defining "ShipmentShipped" must be preceded by "ShipmentPacked"
    When event "ShipmentPacked" occurs
    When event "ShipmentShipped" occurs
    Then the status of "prec_multi" should be "FULFILLED"
    When event "ShipmentShipped" occurs
    Then the status of "prec_multi" should be "TEMPORARY_VIOLATION"



  Scenario: Interleaved irrelevant events don't break precedence
    Given a "Precedence" constraint named "prec_irrelevant" defining "ShipmentShipped" must be preceded by "ShipmentPacked"
    When event "ShipmentPacked" occurs
    When event "IrrelevantEvent" occurs
    When event "ShipmentShipped" occurs
    Then the status of "prec_irrelevant" should be "FULFILLED"