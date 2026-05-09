package de.ur.service.constraint;

import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.dto.ConditionRequest;
import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
import de.ur.service.EplQueryHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public abstract class BaseConstraintHandler implements ConstraintHandler {
    @Inject
    protected EsperService esperService;
    @Inject
    protected ConstraintService constraintService;

    @Override
    public void createDetectionQuery(StatementType type, String name, de.ur.dao.Event event, ConditionRequest condition, CorrelationCondition correlation, java.util.Set<String> relevantKeys, ConditionRequest activationCondition, ConditionRequest targetCondition) {
        if (condition.timer() == null) {

            // --- 1. Build the SELECT clause ---
            java.util.StringJoiner selectClause = new java.util.StringJoiner(", ");
            selectClause.add("id");
            selectClause.add("'" + name + "' as name");
            selectClause.add("'" + type + "' as type");
            selectClause.add("timestamp as timestamp");
            selectClause.add("payload as payload");

            String query = "INSERT INTO constraintStatus SELECT " + selectClause + " FROM GenericEvent";

            // --- 2. Build the WHERE clause from a list of all conditions ---
            java.util.List<String> conditions = new java.util.ArrayList<>();

            // Condition A: The base event type
            conditions.add("eventType = '" + event.name() + "'");

            // Condition B: The simple activation/target condition
            if (EplQueryHelper.isConditionValid(condition)) {
                conditions.add(EplQueryHelper.toEplCondition(condition));
            }

            // Condition C: The correlation existence check (for activations only)
            if (type == StatementType.ACTIVATION && EplQueryHelper.isCorrelationValid(correlation)) {
                String existenceCheck = "payload('" + correlation.activationParam() + "') IS NOT NULL";
                conditions.add(existenceCheck);
            }

            // --- 3. Assemble the final query ---
            query += " WHERE " + String.join(" AND ", conditions);

            String statementName = name + "_" + type.name().toLowerCase();

            var statement = esperService.deployStatements(statementName, query);
            addConstraintStatement(name, statement.getDeploymentId(), type, query);
        } else {

            // --- Timer-based detection query using pattern matching ---

            // --- 1. Build the SELECT clause with t1 prefix ---
            java.util.StringJoiner selectClause = new java.util.StringJoiner(", ");
            selectClause.add("t1.id AS id");
            selectClause.add("'" + name + "' AS name");
            selectClause.add("'" + type + "' AS type");
            selectClause.add("t1.timestamp AS timestamp");
            selectClause.add("t1.payload AS payload");

            String query = "INSERT INTO constraintStatus SELECT " + selectClause + " FROM pattern [";

            // --- 2. Build the pattern conditions ---
            java.util.List<String> patternConditions = new java.util.ArrayList<>();

            // Condition A: The base event type
            patternConditions.add("eventType = '" + event.name() + "'");

            // Condition B: The simple activation/target condition
            if (EplQueryHelper.isConditionValid(condition)) {
                patternConditions.add(EplQueryHelper.toEplCondition(condition));
            }

            // Condition C: The correlation existence check (for activations only)
            if (type == StatementType.ACTIVATION && EplQueryHelper.isCorrelationValid(correlation)) {
                String existenceCheck = "payload('" + correlation.activationParam() + "') IS NOT NULL";
                patternConditions.add(existenceCheck);
            }

            // --- 3. Build the pattern clause with timer guard ---
            query += "every t1 = GenericEvent(" + String.join(", ", patternConditions) + ")";
            
            if (EplQueryHelper.isConditionValid(condition)) {
                String negatedCondition = negateCondition(condition);
                query += " -> (timer:interval(" + condition.timer() + " sec) ";
                query += "and not GenericEvent(eventType = '" + event.name() + "', " + negatedCondition + "))";
            } else {
                query += " -> timer:interval(" + condition.timer() + " sec)";
            }
            
            query += "]";

            String statementName = name + "_" + type.name().toLowerCase();

            var statement = esperService.deployStatements(statementName, query);
            addConstraintStatement(name, statement.getDeploymentId(), type, query);
        }

    }

    String appendCondition(String baseQuery, String conditionPart) {
        // Do nothing if the new condition is null or empty
        if (conditionPart == null || conditionPart.isBlank()) {
            return baseQuery;
        }

        // Check if a WHERE clause already exists (case-insensitive)
        // FIXME will also trigger if "WHERE" is included in any parameter name
        if (baseQuery.toUpperCase().contains("WHERE")) {
            return baseQuery + " AND " + conditionPart;
        } else {
            return baseQuery + " WHERE " + conditionPart;
        }
    }

    private String negateCondition(ConditionRequest condition) {
        String negatedOperator = switch (condition.operator()) {
            case ">" -> "<=";
            case ">=" -> "<";
            case "<" -> ">=";
            case "<=" -> ">";
            case "=" -> "!=";
            case "!=" -> "=";
            default -> throw new IllegalArgumentException("Unsupported operator: " + condition.operator());
        };

        String safeParam = condition.param().replace("'", "''");

        // Check for boolean type
        if ("true".equalsIgnoreCase(condition.value()) || "false".equalsIgnoreCase(condition.value())) {
            String booleanLiteral = condition.value().toUpperCase();
            return "cast(payload('%s'), boolean) %s %s".formatted(safeParam, negatedOperator, booleanLiteral);
        }

        // Check for numeric type
        try {
            new java.math.BigDecimal(condition.value());
            return "cast(payload('%s'), double) %s %s".formatted(safeParam, negatedOperator, condition.value());
        } catch (NumberFormatException e) {
            // Default to string type
            String safeValue = condition.value().replace("'", "''");
            return "payload('%s') %s '%s'".formatted(safeParam, negatedOperator, safeValue);
        }
    }

    protected void addConstraintStatement(String name, String eplId, StatementType eplType, String eplStatement) {
        constraintService.addConstraintStatement(name, eplId, eplType, eplStatement);
    }
}
