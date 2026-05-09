package de.ur.service;

import de.ur.dao.CorrelationCondition;
import de.ur.dto.ConditionRequest;

import java.util.Set;

/**
 * Generates EPL query fragments from condition/correlation DTOs.
 * Keeps EPL-generation logic out of pure data carriers.
 */
public class EplQueryHelper {

    private static final Set<String> NUMERIC_OPERATORS = Set.of("<", ">", "<=", ">=");

    public static boolean isConditionValid(ConditionRequest condition) {
        return condition != null
                && condition.param() != null && !condition.param().isBlank()
                && condition.operator() != null && !condition.operator().isBlank()
                && condition.value() != null && !condition.value().isBlank();
    }

    public static boolean isCorrelationValid(CorrelationCondition correlation) {
        return correlation != null
                && correlation.activationParam() != null && !correlation.activationParam().isBlank()
                && correlation.operator() != null && !correlation.operator().isBlank()
                && correlation.targetParam() != null && !correlation.targetParam().isBlank();
    }

    public static String toEplCondition(ConditionRequest condition) {
        String safeParam = condition.param().replace("'", "''");
        String validOperator = "==".equals(condition.operator()) ? "=" : condition.operator();

        // Check for boolean type
        if ("true".equalsIgnoreCase(condition.value()) || "false".equalsIgnoreCase(condition.value())) {
            String booleanLiteral = condition.value().toUpperCase();
            return "cast(payload('%s'), boolean) %s %s".formatted(safeParam, validOperator, booleanLiteral);
        }

        // Check for numeric type
        try {
            new java.math.BigDecimal(condition.value());
            return "cast(payload('%s'), double) %s %s".formatted(safeParam, validOperator, condition.value());
        } catch (NumberFormatException e) {
            // Default to string type
            String safeValue = condition.value().replace("'", "''");
            return "payload('%s') %s '%s'".formatted(safeParam, validOperator, safeValue);
        }
    }

    public static String toEplCorrelation(CorrelationCondition correlation) {
        String safeActivationParam = correlation.activationParam().replace("'", "''");
        String safeTargetParam = correlation.targetParam().replace("'", "''");
        String validOperator = "==".equals(correlation.operator()) ? "=" : correlation.operator();

        if (NUMERIC_OPERATORS.contains(correlation.operator())) {
            return "cast(a.payload('%s'), double) %s cast(b.payload('%s'), double)"
                    .formatted(safeActivationParam, validOperator, safeTargetParam);
        } else {
            return "a.payload('%s') %s b.payload('%s')"
                    .formatted(safeActivationParam, validOperator, safeTargetParam);
        }
    }
}
