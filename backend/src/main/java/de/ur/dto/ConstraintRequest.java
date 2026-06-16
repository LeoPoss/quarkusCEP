package de.ur.dto;

import de.ur.dao.CorrelationCondition;

public record ConstraintRequest(
        String name,
        String activationEvent,
        String targetEvent,
        Long timer,
        String activationEventType,
        String targetEventType,
        ConditionRequest targetCondition,
        ConditionRequest activationCondition,
        CorrelationCondition correlationCondition,
        Boolean autoExecute,
        String autoExecutePayload
) {
}
