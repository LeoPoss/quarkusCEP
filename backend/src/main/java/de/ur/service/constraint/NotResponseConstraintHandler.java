package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.StatementType;
import de.ur.service.GenericStatusUpdateListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotResponseConstraintHandler extends BaseConstraintHandler {
    @Override
    public ConstraintType getType() {
        return ConstraintType.NOT_RESPONSE;
    }

    @Override
    public void createFulfillmentQuery(String name) {
        // NotResponse doesn't have a fulfillment query as it's about the absence of an
        // event
    }

    @Override
    public void createTemporaryViolationQuery(String name) {

    }

    @Override
    public void createPermanentViolationQuery(String name) {
        String query = """
                SELECT b.id, b.name, b.type, b.timestamp as timestamp
                FROM PATTERN [
                    every a=constraintStatus(type='ACTIVATION', name='%s')
                    -> b=constraintStatus(type='TARGET', name='%s')
                ]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name + "_perm_vio", query);
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
