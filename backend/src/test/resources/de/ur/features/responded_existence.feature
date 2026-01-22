Feature: Responded Existence Constraint Behavior
  "QuestionAsked" and "AnswerProvided" must coexist

  Scenario: Fulfills if QuestionAsked then AnswerProvided
    Given a "Responded Existence" constraint named "coexist1" defining "QuestionAsked" must be responded to by "AnswerProvided"
    When event "QuestionAsked" occurs
    Then the status of "coexist1" should be "TEMPORARY_VIOLATION"
    When event "AnswerProvided" occurs
    Then the status of "coexist1" should be "FULFILLED"

  Scenario: Fulfills if AnswerProvided then QuestionAsked (future match)
    Given a "Responded Existence" constraint named "coexist2" defining "QuestionAsked" must be responded to by "AnswerProvided"
    When event "AnswerProvided" occurs
    When event "QuestionAsked" occurs
    Then the status of "coexist2" should be "FULFILLED"

  Scenario: Interleaved irrelevant events don't break responded existence
    Given a "Responded Existence" constraint named "coexist_irrelevant" defining "QuestionAsked" must be responded to by "AnswerProvided"
    When event "QuestionAsked" occurs
    When event "IrrelevantEvent" occurs
    When event "AnswerProvided" occurs
    Then the status of "coexist_irrelevant" should be "FULFILLED"

  Scenario: Multiple activations (Question, Question) satisfied by single response (Answer)
    Given a "Responded Existence" constraint named "coexist_multi" defining "QuestionAsked" must be responded to by "AnswerProvided"
    When event "QuestionAsked" occurs
    When event "QuestionAsked" occurs
    When event "AnswerProvided" occurs
    Then the status of "coexist_multi" should be "FULFILLED"