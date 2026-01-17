Feature: Not Response Constraint Behavior
  "OrderCancelled must not be followed by ShipmentShipped"

  Scenario: Fulfills if OrderCancelled occurs and NO ShipmentShipped follows (within time window)
    Given a "Not Response" constraint named "notResp1" defining "OrderCancelled" must not be followed by "ShipmentShipped"
    When event "OrderCancelled" occurs
    Then the status of "notResp1" should be "TEMPORARY_VIOLATION"
    When 6 seconds pass
    Then the status of "notResp1" should be "FULFILLED"

  Scenario: Violates if OrderCancelled is followed by ShipmentShipped
    Given a "Not Response" constraint named "notResp2" defining "OrderCancelled" must not be followed by "ShipmentShipped"
    When event "OrderCancelled" occurs
    Then the status of "notResp2" should be "TEMPORARY_VIOLATION"
    When event "ShipmentShipped" occurs
    Then the status of "notResp2" should be "PERMANENT_VIOLATION"

  Scenario: Interleaved irrelevant events don't break Not Response check
    Given a "Not Response" constraint named "notResp_irrelevant" defining "OrderCancelled" must not be followed by "ShipmentShipped"
    When event "OrderCancelled" occurs
    Then the status of "notResp_irrelevant" should be "TEMPORARY_VIOLATION"
    When event "IrrelevantEvent" occurs
    When 6 seconds pass
    Then the status of "notResp_irrelevant" should be "FULFILLED"

