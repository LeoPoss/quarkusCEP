package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChainPrecedenceConstraintHandler extends BaseConstraintHandler {
    @Override
    public ConstraintType getType() {
        return ConstraintType.CHAIN_PRECEDENCE;
    }

    @Override
    public void createFulfillmentQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM PATTERN [every a=constraintStatus(type='ACTIVATION', name='%s') -> b=constraintStatus(type='TARGET', name='%s')]
                """
                .formatted(name, name);

        if (correlation != null && correlation.isValid()) {
            query = appendCondition(query, correlation.getCorrelationQueryPart());
        }

        var statement = esperService.deployStatements(name + "_fulfill", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, true, constraintService,
                esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    @Override
    public void createTemporaryViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                SELECT id, name, type, timestamp
                FROM constraintStatus
                WHERE name = '%s' AND type = 'TARGET'
                """.formatted(name);

        var statement = esperService.deployStatements(name + "_temp_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.TEMPORARY_VIOLATION, false,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    @Override
    public void createPermanentViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        // Fetch activation event name
        var constraint = constraintService.getConstraints().get(name);
        String activationEventName = constraint.getActivationEvent().name();

        // 1. Detect if Target is preceded by any event that is NOT the Activation
        // Pattern: pair (GenericEvent, Target) where first != Activation
        String query = """
                SELECT b.id, '%2$s' as name, 'PERMANENT_VIOLATION' as type, b.timestamp as timestamp
                FROM PATTERN [ every a=GenericEvent -> b=constraintStatus(type='TARGET', name='%2$s') ]
                HAVING a.eventType != '%3$s'
                """.formatted(name, name, activationEventName);

        if (correlation != null && correlation.isValid()) {
            // Add correlation check
            query = """
                    SELECT b.id, '%2$s' as name, 'PERMANENT_VIOLATION' as type, b.timestamp as timestamp
                    FROM PATTERN [ every a=GenericEvent -> b=constraintStatus(type='TARGET', name='%2$s') ]
                    HAVING NOT (a.eventType = '%3$s' AND cast(b.payload['%4$s'], string) = cast(a.payload['%5$s'], string))
                    """
                    .formatted(name, name, activationEventName, correlation.targetParam(),
                            correlation.activationParam());
        }

        var statement = esperService.deployStatements(name + "_perm_vio_chain", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);

        // 2. Detect Initial Violation (Target at start of stream)
        // Pattern: (not GenericEvent) until Target
        String initialQuery = """
                SELECT b.id, '%2$s' as name, 'PERMANENT_VIOLATION' as type, b.timestamp as timestamp
                FROM PATTERN [ (not GenericEvent) until b=constraintStatus(type='TARGET', name='%2$s') ]
                """.formatted(name, name);

        var initStatement = esperService.deployStatements(name + "_perm_vio_init", initialQuery);
        initStatement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                constraintService, esperService));
        addConstraintStatement(name, initStatement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, initialQuery);
    }
}
