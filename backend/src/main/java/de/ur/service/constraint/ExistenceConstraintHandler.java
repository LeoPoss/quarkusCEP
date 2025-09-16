package de.ur.service.constraint;

import com.espertech.esper.common.client.EventBean;
import de.ur.dao.ConstraintStatus;
import de.ur.dao.StatementType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExistenceConstraintHandler extends BaseConstraintHandler {
    @Override
    public void createFulfillmentQuery(String name) {
        String query = """
                SELECT id, name, type
                FROM constraintStatus
                WHERE name = '%s' AND type = 'TARGET'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);
        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    constraintService.getLogger().info("Reacting to fulfillment of: {}", newEvent.getUnderlying());
                    constraintService.getConstraints().get(name).updateStatus(ConstraintStatus.FULFILLED);
                    esperService.removeConstraint(name);
                }
            }
        });
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    @Override
    public void createTemporaryViolationQuery(String name) {
        // No temporary violation for existence constraint
    }
}
