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
        String validOperator = "==".equals(this.operator) ? "=" : this.operator;

        // Check if the operator requires a numeric cast
        if (NUMERIC_OPERATORS.contains(operator)) {
            // Generate EPL with explicit casting for numeric types
            return "cast(a.payload('%s'), double) %s cast(b.payload('%s'), double)"
                    .formatted(safeActivationParam, validOperator, safeTargetParam);
        } else {
            // Generate standard EPL for non-numeric types (e.g., '=', '!=')
            return "a.payload('%s') %s b.payload('%s')"
                    .formatted(safeActivationParam, validOperator, safeTargetParam);
        }
    }
}
