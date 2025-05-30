package de.ur.dto;

public record ConditionRequest(String param, String operator, String value) {
    public boolean isValid() {
        return (param != null && operator != null && value != null && !param.isBlank() && !operator.isBlank() && !value.isBlank());
    }

    public String getConditionQueryPart() {

        String querySegment;

        if ("true".equalsIgnoreCase(this.value) || "false".equalsIgnoreCase(this.value)) {
            String booleanLiteral = this.value.toUpperCase();
            querySegment = " AND cast(payload('%s'), boolean) %s %s".formatted(this.param, this.operator, booleanLiteral);
        } else {
            try {
                querySegment = " AND cast(payload('%s'), double) %s %s".formatted(this.param, this.operator, this.value);
            } catch (NumberFormatException e) {
                String sqlSafeString = this.value.replace("'", "''");
                querySegment = " AND payload('%s') %s '%s'".formatted(this.param, this.operator, sqlSafeString);
            }
        }
        return querySegment;
    }
}