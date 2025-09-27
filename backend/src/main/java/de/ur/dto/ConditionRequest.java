package de.ur.dto;

import java.math.BigDecimal;

public record ConditionRequest(String param, String operator, String value) {
    public boolean isValid() {
        return (param != null && operator != null && value != null && !param.isBlank() && !operator.isBlank() && !value.isBlank());
    }

    public String getConditionQueryPart() {
        String safeParam = this.param.replace("'", "''");

        if ("true".equalsIgnoreCase(this.value) || "false".equalsIgnoreCase(this.value)) {
            String booleanLiteral = this.value.toUpperCase();
            return " AND cast(payload('%s'), boolean) %s %s".formatted(safeParam, this.operator, booleanLiteral);
        }

        try {
            new BigDecimal(this.value);
            return " AND cast(payload('%s'), double) %s %s".formatted(safeParam, this.operator, this.value);
        } catch (NumberFormatException e) {
            String safeValue = this.value.replace("'", "''");
            return " AND payload('%s') %s '%s'".formatted(safeParam, this.operator, safeValue);
        }
    }
}