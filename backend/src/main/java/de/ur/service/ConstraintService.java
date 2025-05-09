package de.ur.service;

import com.espertech.esper.common.client.EventBean;
import de.ur.dao.*;
import de.ur.resource.ConstraintResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class ConstraintService {
    @Inject
    EsperService esperService;

    @Getter
    private ConcurrentHashMap<String, Constraint> constraints = new ConcurrentHashMap<>();

    public void addConstraint(String name, ConstraintType type, String activationEvent, String targetEvent, ConstraintStatus status) {
        constraints.put(name, new Constraint(name, new ArrayList<>(), activationEvent, targetEvent, type, status));
    }

    public void addConstraintStatement(String name, String eplId, StatementType eplType, String eplStatement) {
        Constraint constraint = constraints.get(name);
        constraint.getEplStatements().add(new EplStatement(eplId, eplStatement, eplType));
    }

    public void createExistenceActivationQuery(String name, String targetEvent, ConstraintResource.ConditionRequest targetCondition) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent WHERE type = '%s'
                """.formatted(name, targetEvent);

        if (targetCondition != null) {
            // TODO Cast correctly if string, what else?
            query += "AND cast(payload('%s'), double)%s%s".formatted(targetCondition.param(), targetCondition.condition(), targetCondition.value());
        }

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.ACTIVATION, query);
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

                    constraints.get(name).updateStatus(ConstraintStatus.FULFILLED);
                    esperService.removeConstraint(name);
                }
            }
        });

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    public void createResponseActivationQuery(String name, String activationEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent(type = '%s')
                """.formatted(name, activationEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.ACTIVATION, query);
    }

    public void createResponseTargetQuery(String name, String targetEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'target' as type
                FROM SampleEvent(type = '%s')
                """.formatted(name, targetEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TARGET, query);
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

                    constraints.get(name).updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
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

                    constraints.get(name).updateStatus(ConstraintStatus.FULFILLED);
                    esperService.removeConstraint(name);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    public void createPrecedenceActivationQuery(String name, String activationEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent WHERE type = '%s'
                """.formatted(name, activationEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.ACTIVATION, query);
    }

    public void createPrecedenceTargetQuery(String name, String targetEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'target' as type
                FROM SampleEvent(type = '%s')
                """.formatted(name, targetEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TARGET, query);
    }

    public void createPrecedenceTempViolationQuery(String name) {
        String query = """
                SELECT id, name, type
                FROM constraintStatus
                WHERE name = '%s' AND type = 'activation'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to tempvio of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    public void createPrecedenceFulfillmentQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [every a=constraintStatus(type='target', name='%s') -> b=constraintStatus(type='activation', name='%s')]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to fulfillment of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.FULFILLED);
                    esperService.removeConstraint(name);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    public void createPrecedencePermanentViolationQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [every a=constraintStatus(type='activation', name='%s') -> (timer:interval(1 sec) and not b=constraintStatus(type='target', name='%s'))]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to permanentvio of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.PERMANENT_VIOLATION);
                    esperService.removeConstraint(name);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }

    public void createRespondedExistenceActivationQuery(String name, String activationEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent WHERE type = '%s'
                """.formatted(name, activationEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.ACTIVATION, query);
    }

    public void createRespondedExistenceTargetQuery(String name, String targetEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'target' as type
                FROM SampleEvent(type = '%s')
                """.formatted(name, targetEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TARGET, query);
    }


    public void createRespondedExistenceForwardTempViolationQuery(String name) {
        String query = """
                SELECT id, name, type
                FROM constraintStatus
                WHERE name = '%s' AND type = 'activation'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to tempvio of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
                }
            }
        });

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    public void createRespondedExistenceBackwardTempViolationQuery(String name) {
        String query = """
                SELECT id, name, type
                FROM constraintStatus
                WHERE name = '%s' AND type = 'target'
                """.formatted(name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to tempvio of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
                }
            }
        });

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    public void createRespondedExistenceForwardFulfillmentQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [every a=constraintStatus(type='activation', name='%s') -> b=constraintStatus(type='target', name='%s')]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to forward fulfillment of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.FULFILLED);
                    esperService.removeConstraint(name);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    public void createRespondedExistenceBackwardFulfillmentQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [every a=constraintStatus(type='target', name='%s') -> b=constraintStatus(type='activation', name='%s')]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to backward fullfillment of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.FULFILLED);
                    esperService.removeConstraint(name);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    public void createAlternateResponseActivationQuery(String name, String activationEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent WHERE type = '%s'
                """.formatted(name, activationEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.ACTIVATION, query);
    }

    public void createAlternateResponseTargetQuery(String name, String targetEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'target' as type
                FROM SampleEvent(type = '%s')
                """.formatted(name, targetEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TARGET, query);
    }

    public void createAlternateResponseTempViolationQuery(String name) {
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

                    constraints.get(name).updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    public void createAlternateResponseFulfillmentQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [every a=constraintStatus(type='activation', name='%s') -> b=constraintStatus(type='target', name='%s')]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to fulfillment of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.FULFILLED);
                    esperService.removeConstraint(name);
                }
            }
        });


        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    public void createAlternateResponsePermanentViolationQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [every a=constraintStatus(type='activation', name='%s') -> b=constraintStatus(type='activation', name='%s') -> c=constraintStatus(type='target', name='%s')]
                """.formatted(name, name, name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to permanent violation of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.PERMANENT_VIOLATION);
                    esperService.removeConstraint(name);
                }
            }
        });

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.FULFILLMENT, query);
    }

    public void createNotResponseActivationQuery(String name, String activationEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'activation' as type
                FROM SampleEvent WHERE type = '%s'
                """.formatted(name, activationEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.ACTIVATION, query);
    }

    public void createNotResponseTargetQuery(String name, String targetEvent) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, 'target' as type
                FROM SampleEvent(type = '%s')
                """.formatted(name, targetEvent);

        var statement = esperService.deployStatements(name, query);

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TARGET, query);
    }

    public void createNotResponseTempViolationQuery(String name) {
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

                    constraints.get(name).updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
                }
            }
        });

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.TEMPORARY_VIOLATION, query);
    }

    public void createNotResponsePermanentViolationQuery(String name) {
        String query = """
                SELECT a.id, a.name, a.type
                FROM PATTERN [every a=constraintStatus(type='activation', name='%s') -> b=constraintStatus(type='target', name='%s')]
                """.formatted(name, name);

        var statement = esperService.deployStatements(name, query);

        statement.addListener((newEvents, oldEvents, s, r) -> {
            if (newEvents != null) {
                for (EventBean newEvent : newEvents) {
                    log.info("Reacting to fulfillment of: {}", newEvent.getUnderlying());

                    constraints.get(name).updateStatus(ConstraintStatus.PERMANENT_VIOLATION);
                    esperService.removeConstraint(name);
                }
            }
        });

        addConstraintStatement(name, statement.getDeploymentId(), StatementType.PERMANENT_VIOLATION, query);
    }
}
