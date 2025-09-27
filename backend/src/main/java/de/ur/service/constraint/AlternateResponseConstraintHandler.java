package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
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
    public void createFulfillmentQuery(String name) {
        String query = """
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM pattern [
                    every a=constraintStatus(type='ACTIVATION', name='%1$s')
                    -> b=constraintStatus(type='TARGET', name='%1$s')
                ]
                """.formatted(name);

        var statement = esperService.deployStatements(name + "_fulfill", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.FULFILLED, true, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    @Override
    public void createTemporaryViolationQuery(String name) {
        String query = """
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM pattern [
                    every (
                        a=constraintStatus(type='ACTIVATION', name='%1$s')
                        -> (timer:interval(1 sec) and not b=constraintStatus(type='TARGET', name='%1$s'))
                    )
                ]
                """.formatted(name);

        var statement = esperService.deployStatements(name + "_temp_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.TEMPORARY_VIOLATION, false, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    @Override
    public void createPermanentViolationQuery(String name) {
        String query = """
                SELECT c.id, c.name, c.type, c.timestamp as timestamp
                FROM pattern [
                    every (
                        a=constraintStatus(type='ACTIVATION', name='%1$s')
                        -> b=constraintStatus(type='ACTIVATION', name='%1$s')
                        -> c=constraintStatus(type='TARGET', name='%1$s')
                    )
                ]
                """.formatted(name);

        var statement = esperService.deployStatements(name + "_perm_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
