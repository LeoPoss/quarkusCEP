package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AlternatePrecedenceConstraintHandler extends BaseConstraintHandler {
        @Override
        public ConstraintType getType() {
                return ConstraintType.ALTERNATE_PRECEDENCE;
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
                statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, false,
                                constraintService, esperService));
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
                // Pattern: Activation -> Target -> Target (Violation)
                String query = """
                                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                                FROM PATTERN [every a=constraintStatus(type='ACTIVATION', name='%1$s') -> c=constraintStatus(type='TARGET', name='%1$s') -> b=constraintStatus(type='TARGET', name='%1$s')]
                                """
                                .formatted(name);

                if (correlation != null && correlation.isValid()) {
                        query = appendCondition(query, correlation.getCorrelationQueryPart());
                }

                var statement = esperService.deployStatements(name + "_perm_vio", query);
                statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                                constraintService, esperService));
                addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);

                // Also add logic to detect INITIAL violation (B without any preceding A)
                // Pattern: (not A) until B
                String initialQuery = """
                                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                                FROM PATTERN [(not constraintStatus(type='ACTIVATION', name='%1$s')) until b=constraintStatus(type='TARGET', name='%1$s')]
                                """
                                .formatted(name);

                // Note: correlation challenging here for initial check, omitting for simplicity
                // of this fix (assumes global scope if no A)

                var initialStmt = esperService.deployStatements(name + "_perm_vio_init", initialQuery);
                initialStmt.addListener(
                                new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                                                constraintService, esperService));
                addConstraintStatement(name, initialStmt.getDeploymentId(), StatementType.PERMANENT_VIOLATION,
                                initialQuery);
        }
}
