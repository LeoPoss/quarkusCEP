Feature: Response Constraint Behavior
  "OrderPlaced must be eventually followed by PaymentReceived"

  Scenario: Fulfills if OrderPlaced then PaymentReceived
    Given a "Response" constraint named "resp1" defining "OrderPlaced" must be eventually followed by "PaymentReceived"
    When event "OrderPlaced" occurs
    Then the status of "resp1" should be "TEMPORARY_VIOLATION"
    When event "PaymentReceived" occurs
    Then the status of "resp1" should be "FULFILLED"

  Scenario: Multiple interleaved activations don't block fulfillment
    Given a "Response" constraint named "resp_interleave" defining "OrderPlaced" must be eventually followed by "PaymentReceived"
    When event "OrderPlaced" occurs
    And event "OrderPlaced" occurs
    Then the status of "resp_interleave" should be "TEMPORARY_VIOLATION"
    When event "PaymentReceived" occurs
    Then the status of "resp_interleave" should be "FULFILLED"

  Scenario: Interleaved irrelevant events don't affect status
    Given a "Response" constraint named "resp_irrelevant" defining "OrderPlaced" must be eventually followed by "PaymentReceived"
    When event "OrderPlaced" occurs
    Then the status of "resp_irrelevant" should be "TEMPORARY_VIOLATION"
    When event "IrrelevantEvent" occurs
    Then the status of "resp_irrelevant" should be "TEMPORARY_VIOLATION"
    When event "PaymentReceived" occurs
    Then the status of "resp_irrelevant" should be "FULFILLED"

  Scenario: Re-activation after fulfillment triggers new violation
    Given a "Response" constraint named "resp_reactivate" defining "OrderPlaced" must be eventually followed by "PaymentReceived"
    When event "OrderPlaced" occurs
    And event "PaymentReceived" occurs
    Then the status of "resp_reactivate" should be "FULFILLED"
    When event "OrderPlaced" occurs
    Then the status of "resp_reactivate" should be "TEMPORARY_VIOLATION"