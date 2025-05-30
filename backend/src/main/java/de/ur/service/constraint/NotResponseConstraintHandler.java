package de.ur.service.constraint;

import com.espertech.esper.common.client.EventBean;
import de.ur.dao.ConstraintStatus;
import de.ur.dao.StatementType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotResponseConstraintHandler extends BaseConstraintHandler {

    @Override
    public void createFulfillmentQuery(String name) {
        // NotResponse doesn't have a fulfillment query as it's about the absence of an
        // event
    }

    @Override
    public void createTemporaryViolationQuery(String name) {
        String query = """
                SELECT id, name, type
                FROM constraintStatus
                WHERE name = '%s' AND type = 'ACTIVATION'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    constraintService.getLogger().info("Reacting to temporary violation of: {}", newEvent.getUnderlying());
                    constraintService.getConstraints().get(name).updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
                }
            }
        });

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    @Override
    public void createPermanentViolationQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [
                    every a=constraintStatus(type='ACTIVATION', name='%s')
                    -> b=constraintStatus(type='TARGET', name='%s')
                ]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name + "_perm_vio", query);
        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    constraintService.getLogger().info("Reacting to permanent violation of: {}", newEvent.getUnderlying());
                    constraintService.getConstraints().get(name).updateStatus(ConstraintStatus.PERMANENT_VIOLATION);
                    esperService.removeConstraint(name);
                }
            }
        });
        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
