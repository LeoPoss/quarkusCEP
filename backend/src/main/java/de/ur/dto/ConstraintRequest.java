package de.ur.dto;

import de.ur.dao.CorrelationCondition;

public record ConstraintRequest(
    String name, 
    String activationEvent,
    String targetEvent,
    String activationEventType,
    String targetEventType,
    ConditionRequest targetCondition, 
    ConditionRequest activationCondition,
    CorrelationCondition correlationCondition
) {
    public ConstraintRequest {
        activationEventType = activationEventType != null ? activationEventType : "signal";
        targetEventType = targetEventType != null ? targetEventType : "task";
    }
}
