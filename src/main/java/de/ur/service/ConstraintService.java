package de.ur.service;

import com.espertech.esper.common.client.EventBean;
import de.ur.dao.Constraint;
import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
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

    public void addConstraint(String name, String eplName, String eplStatement, ConstraintType type) {
        constraints.put(name, new Constraint(name, eplName, eplStatement, type, ConstraintStatus.INIT));
    }

    public void addConstraintStatement(String name, String eplName, String eplStatements) {
        Constraint constraint = constraints.get(name);
        constraint.getEplStatements().put(eplName, eplStatements);
    }

    public void createExistenceActivationQuery(String name, String targetEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent WHERE type = '%s'
                """.formatted(name, targetEvent);

        esperService.deployStatements(name, query);

        addConstraint(name, "activation", query, ConstraintType.EXISTENCE);
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


        addConstraintStatement(name, "activationReaction", query);
    }
}
