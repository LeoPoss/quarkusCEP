package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RespondedExistenceConstraintHandler extends BaseConstraintHandler {
    @Override
    public ConstraintType getType() {
        return ConstraintType.RESPONDED_EXISTENCE;
    }

    @Override
    public void createFulfillmentQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                SELECT a.id, a.name, a.type, a.timestamp as timestamp
                FROM pattern [
                    every (
                        b=constraintStatus(type='TARGET', name='%1$s')
                        -> a=constraintStatus(type='ACTIVATION', name='%1$s')
                    )
                ]
                """.formatted(name);

        if (correlation != null && correlation.isValid()) {
            query = appendCondition(query, correlation.getCorrelationQueryPart());
        }

        var statement = esperService.deployStatements(name + "_fulfill", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, true, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);


        // Also need to handle the case when B happens after A
        String query2 = """
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM pattern [
                    every a=constraintStatus(type='ACTIVATION', name='%1$s')
                    -> b=constraintStatus(type='TARGET', name='%1$s')
                ]
                """.formatted(name);

        if (correlation != null && correlation.isValid()) {
            query2 = appendCondition(query2, correlation.getCorrelationQueryPart());
        }

        var statement2 = esperService.deployStatements(name + "_fulfill_after", query2);
        statement2.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, true, constraintService, esperService));
        addConstraintStatement(name, statement2.getDeploymentId(), StatementType.FULFILLMENT, query2);
    }

    @Override
    public void createTemporaryViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                SELECT id, name, type, timestamp
                FROM constraintStatus
                WHERE name = '%s' AND type = 'ACTIVATION'
                """.formatted(name);

        var statement = esperService.deployStatements(name + "_temp_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.TEMPORARY_VIOLATION, false, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    @Override
    public void createPermanentViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        // Responded existence doesn't have a permanent violation
    }
}
