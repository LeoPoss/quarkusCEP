package de.ur.service;

import de.ur.dao.*;
import de.ur.dto.ConditionRequest;
import de.ur.service.constraint.ConstraintHandlerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class ConstraintService {

    @Inject
    ConstraintHandlerFactory constraintHandlerFactory;

    @Getter
    private ConcurrentHashMap<String, Constraint> constraints = new ConcurrentHashMap<>();

    public org.slf4j.Logger getLogger() {
        return log;
    }

    public void resetConstraints() {
        constraints.clear();
    }

    public void setupConstraint(ConstraintType type, String name, String activationEvent,
                                ConditionRequest activationCondition, String targetEvent,
                                ConditionRequest targetCondition, CorrelationCondition correlationCondition, ConstraintStatus status) {
        // Create default empty conditions if null
        ConditionRequest safeActivationCondition = activationCondition != null ? activationCondition : new ConditionRequest("", "", "");
        ConditionRequest safeTargetCondition = targetCondition != null ? targetCondition : new ConditionRequest("", "", "");

        Set<String> relevantKeys = getRelevantKeys(correlationCondition, safeActivationCondition, safeTargetCondition);

        // Create and add the constraint to the map first
        Constraint constraint = new Constraint(
                name,
                new ArrayList<>(),
                activationEvent,
                safeActivationCondition.isValid() ? new ConstraintCondition(
                        safeActivationCondition.param(),
                        safeActivationCondition.operator(),
                        safeActivationCondition.value()
                ) : null,
                targetEvent,
                safeTargetCondition.isValid() ? new ConstraintCondition(
                        safeTargetCondition.param(),
                        safeTargetCondition.operator(),
                        safeTargetCondition.value()
                ) : null,
                correlationCondition,
                type,
                status
        );
        constraints.put(name, constraint);

        var handler = constraintHandlerFactory.getHandler(type);

        // Only create activation detection query if activationEvent is provided
        if (activationEvent != null && !activationEvent.isBlank()) {
            handler.createDetectionQuery(StatementType.ACTIVATION, name, activationEvent, safeActivationCondition, correlationCondition, relevantKeys);
        }

        // Only create target detection query if targetEvent is provided
        if (targetEvent != null && !targetEvent.isBlank()) {
            handler.createDetectionQuery(StatementType.TARGET, name, targetEvent, safeTargetCondition, correlationCondition, relevantKeys);
        }

        handler.createFulfillmentQuery(name, correlationCondition);
        handler.createTemporaryViolationQuery(name, correlationCondition);
        handler.createPermanentViolationQuery(name, correlationCondition);
    }

    private static Set<String> getRelevantKeys(CorrelationCondition correlationCondition, ConditionRequest safeActivationCondition, ConditionRequest safeTargetCondition) {
        Set<String> relevantKeys = new java.util.HashSet<>();
        if (safeActivationCondition.isValid()) {
            relevantKeys.add(safeActivationCondition.param());
        }
        if (safeTargetCondition.isValid()) {
            relevantKeys.add(safeTargetCondition.param());
        }
        if (correlationCondition != null && correlationCondition.isValid()) {
            relevantKeys.add(correlationCondition.activationParam());
            relevantKeys.add(correlationCondition.targetParam());
        }
        return relevantKeys;
    }

    public void addConstraintStatement(String name, String eplId, StatementType eplType, String eplStatement) {
        Constraint constraint = constraints.get(name);
        constraint.getEplStatements().add(new EplStatement(eplId, eplStatement, eplType));
    }
}
