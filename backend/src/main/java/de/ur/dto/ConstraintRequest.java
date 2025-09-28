package de.ur.dto;

import de.ur.dao.CorrelationCondition;

public record ConstraintRequest(String name, String activationEvent, String targetEvent,
                                ConditionRequest targetCondition, ConditionRequest activationCondition,
                                CorrelationCondition correlationCondition) {
}
