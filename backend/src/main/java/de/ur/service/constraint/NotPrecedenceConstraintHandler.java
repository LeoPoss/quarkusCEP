package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotPrecedenceConstraintHandler extends BaseConstraintHandler {
    @Override
    public ConstraintType getType() {
        return ConstraintType.NOT_PRECEDENCE;
    }

    @Override
    public void createFulfillmentQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                SELECT id, name, type, timestamp
                FROM constraintStatus
                WHERE name = '%s' AND type = 'TARGET'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, false,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);

    }

    @Override
    public void createTemporaryViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
    }

    @Override
    public void createPermanentViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        // Not Precedence: "B must not be preceded by A".
        // Violation: Sequence A -> B matches.
        String query = """
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM PATTERN [every a=constraintStatus(type='ACTIVATION', name='%s') -> b=constraintStatus(type='TARGET', name='%s')]
                """
                .formatted(name, name);

        if (withinPeriod != null) {
            // If timer provided: "B must not be preceded by A within X time"
            // Pattern: every a=Activation -> (b=Target and timer:within(X sec))
            query = """
                    SELECT b.id, b.name, b.type, b.timestamp as timestamp
                    FROM PATTERN [every a=constraintStatus(type='ACTIVATION', name='%s') -> (b=constraintStatus(type='TARGET', name='%s') where timer:within(%d sec))]
                    """
                    .formatted(name, name, withinPeriod);
        }

        if (correlation != null && correlation.isValid()) {
            query = appendCondition(query, correlation.getCorrelationQueryPart());
        }
        var statement = esperService.deployStatements(name, query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
