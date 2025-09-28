package de.ur.dao;

import java.util.Set;

public record CorrelationCondition(
        String activationParam,
        String operator,
        String targetParam
) {
    private static final Set<String> NUMERIC_OPERATORS = Set.of("<", ">", "<=", ">=");

    public boolean isValid() {
        return activationParam != null && !activationParam.isBlank() &&
                operator != null && !operator.isBlank() &&
                targetParam != null && !targetParam.isBlank();
    }

    public String getCorrelationQueryPart() {
        String safeActivationParam = activationParam.replace("'", "''");
        String safeTargetParam = targetParam.replace("'", "''");

        // Check if the operator requires a numeric cast
        if (NUMERIC_OPERATORS.contains(operator)) {
            // Generate EPL with explicit casting for numeric types
            return "cast(a.%s, double) %s cast(b.%s, double)"
                    .formatted(safeActivationParam, operator, safeTargetParam);
        } else {
            // Generate standard EPL for non-numeric types (e.g., '=', '!=')
            return "a.%s %s b.%s"
                    .formatted(safeActivationParam, operator, safeTargetParam);
        }
    }
}
