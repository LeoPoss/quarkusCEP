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
                statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, true,
                                constraintService,
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
                // Fetch activation and target event names
                var constraint = constraintService.getConstraints().get(name);
                String activationEventName = constraint.getActivationEvent().name();
                String targetEventName = constraint.getTargetEvent().name();

                // 1. Detect if Target is preceded by any event that is INVALID (Not Activation
                // OR Correlation Mismatch)
                // We use MATCH_RECOGNIZE on the GenericEvent stream for strict sequence
                // monitoring

                String correlationCheck = "";
                if (correlation != null && correlation.isValid()) {
                        // OR (A is Activation but payload mismatch)
                        correlationCheck = """
                                        OR (A.eventType = '%s' AND cast(B.payload['%s'], string) != cast(A.payload['%s'], string))
                                        """
                                        .formatted(activationEventName, correlation.targetParam(),
                                                        correlation.activationParam());
                }

                String query = """
                                SELECT id, '%s' as name, 'PERMANENT_VIOLATION' as type, timestamp as timestamp
                                FROM GenericEvent
                                MATCH_RECOGNIZE (
                                    MEASURES B.id as id, B.timestamp as timestamp
                                    PATTERN (A B)
                                    DEFINE
                                        A as true,
                                        B as B.eventType = '%s' AND (A.eventType != '%s' %s)
                                )
                                """.formatted(name, targetEventName, activationEventName, correlationCheck);

                var statement = esperService.deployStatements(name + "_perm_vio_chain", query);
                statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                                constraintService, esperService));
                addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);

                // 2. Detect Initial Violation (Target at start of stream)
                // Pattern: (not GenericEvent) until GenericEvent(Target).
                // Checks if the FIRST GenericEvent is the Target (meaning no preceding
                // Activation).
                String initialQuery = """
                                SELECT b.id, '%s' as name, 'PERMANENT_VIOLATION' as type, b.timestamp as timestamp
                                FROM PATTERN [ (not GenericEvent) until b=GenericEvent(eventType='%s') ]
                                """.formatted(name, targetEventName);

                var initStatement = esperService.deployStatements(name + "_perm_vio_init", initialQuery);
                initStatement.addListener(
                                new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                                                constraintService, esperService));
                addConstraintStatement(name, initStatement.getDeploymentId(), StatementType.PERMANENT_VIOLATION,
                                initialQuery);
        }
}
