package de.ur.dto;

public record ConditionRequest(String param, String operator, String value, Long timer) {
    public boolean isValid() {
        return (param != null && operator != null && value != null && !param.isBlank() && !operator.isBlank() && !value.isBlank());
    }

    public String getConditionQueryPart() {
        String safeParam = this.param.replace("'", "''");
        String validOperator = "==".equals(this.operator) ? "=" : this.operator;

        // Check for boolean type
        if ("true".equalsIgnoreCase(this.value) || "false".equalsIgnoreCase(this.value)) {
            String booleanLiteral = this.value.toUpperCase();
            // Generates: cast(payload('isCritical'), boolean) = TRUE
            return "cast(payload('%s'), boolean) %s %s".formatted(safeParam, validOperator, booleanLiteral);
        }

        // Check for numeric type
        try {
            new java.math.BigDecimal(this.value);
            // Generates: cast(payload('temp'), double) > 100
            return "cast(payload('%s'), double) %s %s".formatted(safeParam, validOperator, this.value);
        } catch (NumberFormatException e) {
            // Default to string type
            String safeValue = this.value.replace("'", "''");
            // Generates: payload('machineID') = 'M-007'
            return "payload('%s') %s '%s'".formatted(safeParam, validOperator, safeValue);
        }
    }
}