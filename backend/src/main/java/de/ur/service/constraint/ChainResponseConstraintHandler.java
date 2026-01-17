package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChainResponseConstraintHandler extends BaseConstraintHandler {
    @Override
    public ConstraintType getType() {
        return ConstraintType.CHAIN_RESPONSE;
    }

    @Override
    public void createFulfillmentQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                    SELECT b.id, b.name, b.type, b.timestamp as timestamp
                    FROM PATTERN [every a=constraintStatus(type='ACTIVATION', name='%s') -> b=constraintStatus(type='TARGET', name='%s')]
                """
                .formatted(name, name);

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
                WHERE name = '%s' AND type = 'ACTIVATION'
                """.formatted(name);

        var statement = esperService.deployStatements(name + "_temp_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.TEMPORARY_VIOLATION, false,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    @Override
    public void createPermanentViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        // Fetch the target event name to check against
        var constraint = constraintService.getConstraints().get(name);
        String targetEventName = constraint.getTargetEvent().name();

        // Detect if Activation is followed by ANY event that is NOT the Target
        // We must listen to GenericEvent stream to catch unrelated events
        String query = """
                SELECT b.id, '%2$s' as name, 'PERMANENT_VIOLATION' as type, b.timestamp as timestamp
                FROM PATTERN [ every a=constraintStatus(type='ACTIVATION', name='%2$s') -> b=GenericEvent ]
                HAVING b.eventType != '%3$s'
                """.formatted(name, name, targetEventName);

        if (correlation != null && correlation.isValid()) {
            if (correlation != null && correlation.isValid()) {

                query = """
                        SELECT b.id, '%2$s' as name, 'PERMANENT_VIOLATION' as type, b.timestamp as timestamp
                        FROM PATTERN [ every a=constraintStatus(type='ACTIVATION', name='%2$s') -> b=GenericEvent ]
                        HAVING NOT (b.eventType = '%3$s' AND cast(b.payload['%4$s'], string) = cast(a.%5$s, string))
                        """.formatted(name, name, targetEventName, correlation.targetParam(),
                        correlation.activationParam());
            }
        }

        var statement = esperService.deployStatements(name + "_perm_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
