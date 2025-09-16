package de.ur.service;

import de.ur.dao.*;
import de.ur.dto.ConditionRequest;
import de.ur.service.constraint.ConstraintHandlerFactory;
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
                                ConditionRequest targetCondition, ConstraintStatus status) {
        // Create default empty conditions if null
        ConditionRequest safeActivationCondition = activationCondition != null ? activationCondition : new ConditionRequest("", "", "");
        ConditionRequest safeTargetCondition = targetCondition != null ? targetCondition : new ConditionRequest("", "", "");

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
            type, 
            status
        );
        constraints.put(name, constraint);

        var handler = constraintHandlerFactory.getHandler(type);

        // Only create activation detection query if activationEvent is provided
        if (activationEvent != null && !activationEvent.isBlank()) {
            handler.createDetectionQuery(StatementType.ACTIVATION, name, activationEvent, safeActivationCondition);
        }
        
        // Only create target detection query if targetEvent is provided
        if (targetEvent != null && !targetEvent.isBlank()) {
            handler.createDetectionQuery(StatementType.TARGET, name, targetEvent, safeTargetCondition);
        }

        handler.createFulfillmentQuery(name);
        handler.createTemporaryViolationQuery(name);
        handler.createPermanentViolationQuery(name);
    }

    public void addConstraintStatement(String name, String eplId, StatementType eplType, String eplStatement) {
        Constraint constraint = constraints.get(name);
        constraint.getEplStatements().add(new EplStatement(eplId, eplStatement, eplType));
    }
}
