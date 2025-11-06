package de.ur.service.constraint;

import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.dto.ConditionRequest;
import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
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
    public void createDetectionQuery(
            StatementType type, String name, de.ur.dao.Event event,
            ConditionRequest condition,
            CorrelationCondition correlation,
            java.util.Set<String> relevantKeys
    ) {
        // --- 1. Build the SELECT clause ---
        java.util.StringJoiner selectClause = new java.util.StringJoiner(", ");
        selectClause.add("id");
        selectClause.add("'" + name + "' as name");
        selectClause.add("'" + type + "' as type");
        selectClause.add("timestamp");
        for (String key : relevantKeys) {
            selectClause.add("payload('" + key + "') as " + key);
        }

        String query = "INSERT INTO constraintStatus SELECT " + selectClause + " FROM GenericEvent";

        // --- 2. Build the WHERE clause from a list of all conditions ---
        java.util.List<String> conditions = new java.util.ArrayList<>();

        // Condition A: The base event type
        conditions.add("eventType = '" + event.name() + "'");

        // Condition B: The simple activation/target condition
        if (condition != null && condition.isValid()) {
            conditions.add(condition.getConditionQueryPart());
        }

        // Condition C: The correlation existence check (for activations only)
        if (type == StatementType.ACTIVATION && correlation != null && correlation.isValid()) {
            String existenceCheck = "payload('" + correlation.activationParam() + "') IS NOT NULL";
            conditions.add(existenceCheck);
        }

        // --- 3. Assemble the final query ---
        query += " WHERE " + String.join(" AND ", conditions);

        String statementName = name + "_" + type.name().toLowerCase();
        var statement = esperService.deployStatements(statementName, query);
        addConstraintStatement(name, statement.getDeploymentId(), type, query);
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

    protected void addConstraintStatement(String name, String eplId, StatementType eplType, String eplStatement) {
        constraintService.addConstraintStatement(name, eplId, eplType, eplStatement);
    }
}
