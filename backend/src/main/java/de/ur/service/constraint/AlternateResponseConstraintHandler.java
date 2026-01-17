package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AlternateResponseConstraintHandler extends BaseConstraintHandler {
    @Override
    public ConstraintType getType() {
        return ConstraintType.ALTERNATE_RESPONSE;
    }

    @Override
    public void createFulfillmentQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM pattern [
                    every a=constraintStatus(type='ACTIVATION', name='%1$s')
                    -> b=constraintStatus(type='TARGET', name='%1$s')
                ]
                """.formatted(name);

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
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM pattern [
                    every (
                        a=constraintStatus(type='ACTIVATION', name='%1$s')
                        -> (timer:interval(1 sec) and not b=constraintStatus(type='TARGET', name='%1$s'))
                    )
                ]
                """.formatted(name);

        if (correlation != null && correlation.isValid()) {
            query = appendCondition(query, correlation.getCorrelationQueryPart());
        }

        var statement = esperService.deployStatements(name + "_temp_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.TEMPORARY_VIOLATION, false,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    @Override
    public void createPermanentViolationQuery(String name, CorrelationCondition correlation, Long withinPeriod) {
        String query = """
                SELECT nxt.id, nxt.name, nxt.type, nxt.timestamp as timestamp
                FROM pattern [
                    every a=constraintStatus(type='ACTIVATION', name='%1$s')
                    -> nxt=constraintStatus(type in ('ACTIVATION', 'TARGET'), name='%1$s')
                ]
                WHERE nxt.type = 'ACTIVATION'
                """.formatted(name);

        if (correlation != null && correlation.isValid()) {
            query = appendCondition(query, correlation.getCorrelationQueryPart());
        }

        var statement = esperService.deployStatements(name + "_perm_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true,
                constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
