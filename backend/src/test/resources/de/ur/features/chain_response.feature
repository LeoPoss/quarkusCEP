Feature: Chain Response Constraint Behavior
  "PaymentAuthorized must be immediately followed by ReceiptGenerated"

  Scenario: Fulfills if PaymentAuthorized is immediately followed by ReceiptGenerated
    Given a "Chain Response" constraint named "chainResp1" defining "PaymentAuthorized" must be immediately followed by "ReceiptGenerated"
    When event "PaymentAuthorized" occurs
    Then the status of "chainResp1" should be "TEMPORARY_VIOLATION"
    When event "ReceiptGenerated" occurs
    Then the status of "chainResp1" should be "FULFILLED"

  Scenario: Violates if intermediate event occurs
    Given a "Chain Response" constraint named "chainResp2" defining "PaymentAuthorized" must be immediately followed by "ReceiptGenerated"
    When event "PaymentAuthorized" occurs
    Then the status of "chainResp2" should be "TEMPORARY_VIOLATION"
    When event "OtherEvent" occurs
    Then the status of "chainResp2" should be "PERMANENT_VIOLATION"

  Scenario: Multiple interleaved activations don't block fulfillment
    Given a "Chain Response" constraint named "chainResp_interleave" defining "PaymentAuthorized" must be immediately followed by "ReceiptGenerated"
    When event "PaymentAuthorized" occurs
    # Immediate Violation because next event must be ReceiptGenerated
    And event "PaymentAuthorized" occurs
    Then the status of "chainResp_interleave" should be "PERMANENT_VIOLATION"

  Scenario: Interleaved irrelevant events trigger violation
    Given a "Chain Response" constraint named "chainResp_irrelevant" defining "PaymentAuthorized" must be immediately followed by "ReceiptGenerated"
    When event "PaymentAuthorized" occurs
    Then the status of "chainResp_irrelevant" should be "TEMPORARY_VIOLATION"
    When event "IrrelevantEvent" occurs
    Then the status of "chainResp_irrelevant" should be "PERMANENT_VIOLATION"

