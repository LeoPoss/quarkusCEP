package de.ur.dao;

public record CorrelationCondition(
        String activationParam,
        String operator,
        String targetParam
) {
}
