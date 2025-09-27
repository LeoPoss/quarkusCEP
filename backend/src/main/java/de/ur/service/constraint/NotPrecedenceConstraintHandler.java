package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
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
    public void createFulfillmentQuery(String name) {
        String query = """
                SELECT id, name, type, timestamp
                FROM constraintStatus
                WHERE name = '%s' AND type = 'TARGET'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, false, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);

    }

    @Override
    public void createTemporaryViolationQuery(String name) {
    }

    @Override
    public void createPermanentViolationQuery(String name) {
        String query = """
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM PATTERN [every a=constraintStatus(type='TARGET', name='%s') -> (timer:interval(1 sec) and not b=constraintStatus(type='ACTIVATION', name='%s'))]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name, query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
