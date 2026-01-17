package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Handler for NOT_RESPONSE constraints.
 * NOT_RESPONSE(A, B) = "If A occurs, B must NOT follow"
 * 
 * States:
 * - INIT: No activation has occurred
 * - TEMPORARY_VIOLATION: A has occurred (watching for B)
 * - FULFILLED: A occurred and B did not follow (determined at process end or by
 * timer)
 * - PERMANENT_VIOLATION: A occurred and then B followed
 */
@ApplicationScoped
public class NotResponseConstraintHandler extends BaseConstraintHandler {

    @Override
    public ConstraintType getType() {
        return ConstraintType.NOT_RESPONSE;
    }

    @Override
    public void createFulfillmentQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        // NOT_RESPONSE fulfillment: A occurred and B did NOT follow within the period
        // If no timer specified, we can't auto-fulfill - fulfillment is determined at
        // process end
        // For now, we'll use a very long default timer or skip auto-fulfillment

        if (withinPeriod == null) {
            // Without a timer, fulfillment is determined at process completion
            // We don't deploy a fulfillment query in this case
            return;
        }

        String query = """
                INSERT INTO constraintStatus
                SELECT '%s' as name, 'FULFILLMENT' as type, current_timestamp as timestamp
                FROM PATTERN [
                    a=constraintStatus(type='ACTIVATION', name='%s')
                    -> (timer:interval(%d sec) and not b=constraintStatus(type='TARGET', name='%s'))
                ]
                """.formatted(name, name, withinPeriod, name);

        var statement = esperService.deployStatements(name + "_FULFILLMENT", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, true, constraintService,
                esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    @Override
    public void createTemporaryViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        // When A occurs, we enter TEMPORARY_VIOLATION (watching for B)
        String query = """
                SELECT id, name, type, timestamp
                FROM constraintStatus
                WHERE name = '%s' AND type = 'ACTIVATION'
                """.formatted(name);

        var statement = esperService.deployStatements(name + "_temp_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.TEMPORARY_VIOLATION, false,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    @Override
    public void createPermanentViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        // If A occurred and then B occurs, it's a PERMANENT_VIOLATION
        String query = """
                SELECT b.id as id, '%s' as name, 'PERMANENT_VIOLATION' as type, b.timestamp as timestamp
                FROM PATTERN [
                    every a=constraintStatus(type='ACTIVATION', name='%s')
                    -> b=constraintStatus(type='TARGET', name='%s')
                ]
                """.formatted(name, name, name);

        if (correlation != null && correlation.isValid()) {
            query = appendCondition(query, correlation.getCorrelationQueryPart());
        }

        var statement = esperService.deployStatements(name + "_perm_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
