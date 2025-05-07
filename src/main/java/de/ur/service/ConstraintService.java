package de.ur.service;

import com.espertech.esper.common.client.EventBean;
import de.ur.dao.Constraint;
import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.StatementType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class ConstraintService {
    @Inject
    EsperService esperService;

    @Getter
    private ConcurrentHashMap<String, Constraint> constraints = new ConcurrentHashMap<>();

    public void addConstraint(String name, StatementType eplName, String eplStatement, ConstraintType type) {
        addConstraint(name, eplName, eplStatement, type, ConstraintStatus.INIT);
    }

    public void addConstraint(String name, StatementType eplName, String eplStatement, ConstraintType type, ConstraintStatus status) {
        constraints.put(name, new Constraint(name, eplName.name(), eplStatement, type, status));
    }

    public void addConstraintStatement(String name, StatementType eplName, String eplStatements) {
        Constraint constraint = constraints.get(name);
        constraint.getEplStatements().put(eplName.toString(), eplStatements);
    }

    public void createExistenceActivationQuery(String name, String targetEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent WHERE type = '%s'
                """.formatted(name, targetEvent);

        esperService.deployStatements(name, query);

        addConstraint(name, StatementType.ACTIVATION, query, ConstraintType.EXISTENCE, ConstraintStatus.TEMPORARY_VIOLATION);
    }

    public void createExistenceFulfillmentQuery(String name) {
        String query = """
                SELECT id, name, type
                FROM constraintStatus
                WHERE name = '%s' AND type = 'activation'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to activation of: {}", newEvent.getUnderlying());

                    constraints.get(name).setStatus(ConstraintStatus.FULFILLED);
                }
            }
        });


        addConstraintStatement(name, StatementType.FULFILLMENT, query);
    }

    public void createResponseActivationQuery(String name, String activationEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent(type = '%s')
                """.formatted(name, activationEvent);

        esperService.deployStatements(name, query);

        addConstraint(name, StatementType.ACTIVATION, query, ConstraintType.RESPONSE, ConstraintStatus.INIT);
    }

    public void createResponseTargetQuery(String name, String targetEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'target' as type
                FROM SampleEvent(type = '%s')
                """.formatted(name, targetEvent);

        esperService.deployStatements(name, query);

        addConstraintStatement(name, StatementType.TARGET, query);
    }

    public void createResponseTempViolationQuery(String name) {
        String query = """
                SELECT id, name, type
                FROM constraintStatus
                WHERE name = '%s' AND type = 'activation'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to activation of: {}", newEvent.getUnderlying());

                    constraints.get(name).setStatus(ConstraintStatus.TEMPORARY_VIOLATION);
                }
            }
        });


        addConstraintStatement(name, StatementType.TEMPORARY_VIOLATION, query);
    }

    public void createResponseFulfillmentQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [every a=constraintStatus(type='activation', name='%s') -> b=constraintStatus(type='target', name='%s')]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to activation of: {}", newEvent.getUnderlying());

                    constraints.get(name).setStatus(ConstraintStatus.FULFILLED);
                }
            }
        });


        addConstraintStatement(name, StatementType.FULFILLMENT, query);
    }
}
