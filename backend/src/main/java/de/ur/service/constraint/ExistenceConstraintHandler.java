package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExistenceConstraintHandler extends BaseConstraintHandler {
    @Override
    public ConstraintType getType() {
        return ConstraintType.EXISTENCE;
    }

    @Override
    public void createFulfillmentQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                SELECT id, name, type, timestamp
                FROM constraintStatus
                WHERE name = '%s' AND type = 'TARGET'
                """.formatted(name);

        if (correlation != null && correlation.isValid()) {
            query = appendCondition(query, correlation.getCorrelationQueryPart());
        }

        var statement = esperService.deployStatements(name, query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, false,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    @Override
    public void createTemporaryViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        // EXISTENCE constraints start in TEMPORARY_VIOLATION state (set at creation
        // time).
        // No EPL query needed - the constraint will transition to FULFILLED when the
        // target event occurs.
        // Previously this had a pattern that fired on EVERY event, incorrectly
        // resetting all constraints.
    }

    @Override
    public void createPermanentViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        if (withinPeriod != null) {
            String query = """
                            INSERT INTO constraintStatus
                            SELECT '%s' as name, 'PERMANENT_VIOLATION' as type, a.timestamp as timestamp
                            FROM PATTERN [
                                a=constraintStatus(type='ACTIVATION', name='%s')
                                                        -> (timer:interval(%d sec) and not b=constraintStatus(type='TARGET', name='%s'))
                            ]
                    """
                    .formatted(name, name, withinPeriod, name);

            var statement = esperService.deployStatements(name + "_PERM_VIO", query);

            statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                    constraintService, esperService));
            addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
        }
    }
}
