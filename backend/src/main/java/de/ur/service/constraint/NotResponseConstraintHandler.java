package de.ur.service.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
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
    public void createFulfillmentQuery(String name, CorrelationCondition correlation) {
        // NotResponse doesn't have a fulfillment query as it's about the absence of an
        // event
    }

    @Override
    public void createTemporaryViolationQuery(String name, CorrelationCondition correlation) {

    }

    @Override
    public void createPermanentViolationQuery(String name, CorrelationCondition correlation) {
        String query = """
                SELECT b.id as id, b.name as name, b.type as type, b.timestamp as timestamp, b.test as test
                FROM PATTERN [
                    every a=constraintStatus(type='ACTIVATION', name='%s')
                    -> b=constraintStatus(type='TARGET', name='%s')
                ]
                """.formatted(name, name);

        if (correlation != null && correlation.isValid()) {
            query = appendCondition(query, correlation.getCorrelationQueryPart());
        }

        var statement = esperService.deployStatements(name + "_perm_vio", query);
       // var statement2 = esperService.deployStatements(name + "_perm_vio", query, "TestContext");
        statement.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, true, constraintService, esperService));
      //  statement2.addListener(new GenericStatusUpdateListener(name, ConstraintStatus.PERMANENT_VIOLATION, false, constraintService, esperService));
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
