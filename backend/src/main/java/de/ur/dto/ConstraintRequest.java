package de.ur.dto;

public record ConstraintRequest(String name, String activationEvent, String targetEvent,
                              ConditionRequest targetCondition, ConditionRequest activationCondition) {
}
