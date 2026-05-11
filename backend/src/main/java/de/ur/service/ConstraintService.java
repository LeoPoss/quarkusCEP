package de.ur.service;

import de.ur.dao.*;
import de.ur.dto.ConditionRequest;
import de.ur.service.constraint.ConstraintHandlerFactory;
import de.ur.service.EplQueryHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
@Slf4j
public class ConstraintService {

    @Inject
    ConstraintHandlerFactory constraintHandlerFactory;

    @Inject
    EsperService esperService;

    @Getter
    private ConcurrentHashMap<String, Constraint> constraints = new ConcurrentHashMap<>();

    @Getter
    private List<Map<String, Object>> trace = new ArrayList<>();

    public Set<String> getKnownEvents() {
        return getConstraints().values().stream().flatMap(constraint -> Stream.of(constraint.getActivationEvent(), constraint.getTargetEvent())).filter(Objects::nonNull).filter(event -> event.type() == Event.EventType.TASK).map(Event::name).collect(Collectors.toSet());
    }

    public Set<String> getKnownSignals() {
        return getConstraints().values().stream().flatMap(constraint -> Stream.of(constraint.getActivationEvent(), constraint.getTargetEvent())).filter(Objects::nonNull).filter(event -> event.type() == Event.EventType.SIGNAL).map(Event::name).collect(Collectors.toSet());
    }

    public void addToTrace(String eventType, Map<String, String> payload, long timestamp) {
        Map<String, Object> traceEvent = new HashMap<>();
        traceEvent.put("eventType", eventType);
        traceEvent.put("payload", payload != null ? new HashMap<>(payload) : null);
        traceEvent.put("timestamp", timestamp);
        trace.add(traceEvent);
    }


    public void resetConstraints() {
        constraints.clear();
    }

    public void setupConstraint(ConstraintType type, String name, Long withinPeriod, String activationEventName, ConditionRequest activationCondition, String targetEventName, ConditionRequest targetCondition, CorrelationCondition correlationCondition, ConstraintStatus status, String activationEventType, String targetEventType, boolean autoExecute) {
        // Create activation event
        Event activationEvent = null;
        if (activationEventName != null && !activationEventName.isBlank()) {
            Event.EventType eventType = "task".equalsIgnoreCase(activationEventType) ? Event.EventType.TASK : Event.EventType.SIGNAL;
            activationEvent = new Event(activationEventName, eventType);
        }

        // Create target event
        Event targetEvent = null;
        if (targetEventName != null && !targetEventName.isBlank()) {
            Event.EventType eventType = "task".equalsIgnoreCase(targetEventType) ? Event.EventType.TASK : Event.EventType.SIGNAL;
            targetEvent = new Event(targetEventName, eventType);
        }
        // Create default empty conditions if null
        ConditionRequest safeActivationCondition = activationCondition != null ? activationCondition : new ConditionRequest("", "", "", null);
        ConditionRequest safeTargetCondition = targetCondition != null ? targetCondition : new ConditionRequest("", "", "", null);

        Set<String> relevantKeys = getRelevantKeys(correlationCondition, safeActivationCondition, safeTargetCondition);

        // Create and add the constraint to the map first
        Constraint constraint = new Constraint(name, withinPeriod != null ? withinPeriod : null, new ArrayList<>(), activationEvent, EplQueryHelper.isConditionValid(safeActivationCondition) ? new ConstraintCondition(safeActivationCondition.param(), safeActivationCondition.operator(), safeActivationCondition.value(), safeActivationCondition.timer()) : null, targetEvent, EplQueryHelper.isConditionValid(safeTargetCondition) ? new ConstraintCondition(safeTargetCondition.param(), safeTargetCondition.operator(), safeTargetCondition.value(), safeTargetCondition.timer()) : null, correlationCondition, type, status, autoExecute);
        constraints.put(name, constraint);

        var handler = constraintHandlerFactory.getHandler(type);

        // Only create activation detection query if activationEvent is provided
        if (activationEvent != null) {
            handler.createDetectionQuery(StatementType.ACTIVATION, name, activationEvent, safeActivationCondition, correlationCondition, relevantKeys, safeActivationCondition, safeTargetCondition);

            // Auto-execute: when activation fires, automatically inject the target event
            if (autoExecute && targetEvent != null) {
                log.info("Setting up auto-execute for '{}': target='{}'", name, targetEvent.name());
                final String targetName = targetEvent.name();
                // Build target payload that satisfies the constraint's target condition
                final java.util.Map<String, String> targetPayload = new java.util.HashMap<>();
                targetPayload.put("source", "autoexecute");
                targetPayload.put("constraint", name);
                if (safeTargetCondition != null && safeTargetCondition.param() != null && !safeTargetCondition.param().isBlank()) {
                    targetPayload.put(safeTargetCondition.param(), safeTargetCondition.value());
                }
                // Find the deployed ACTIVATION statement and attach a listener directly
                for (var s : constraint.getEplStatements()) {
                    if (s.type() == StatementType.ACTIVATION) {
                        var stmt = esperService.getRuntime().getDeploymentService()
                            .getStatement(s.deploymentId(), name + "_activation");
                        if (stmt != null) {
                            stmt.addListener((newEvents, oldEvents, statement, runtime) -> {
                                if (newEvents != null && newEvents.length > 0) {
                                    log.info("Auto-execute triggered for '{}': injecting '{}' with payload {}",
                                        name, targetName, targetPayload);
                                    GenericEvent target = new GenericEvent(
                                        java.util.UUID.randomUUID().toString(), targetName,
                                        System.currentTimeMillis(), new java.util.HashMap<>(targetPayload));
                                    addToTrace(targetName, target.getPayload(), target.getTimestamp());
                                    esperService.sendEvent(target);
                                }
                            });
                            log.info("Auto-execute listener attached for '{}'", name);
                        } else {
                            log.warn("Could not find ACTIVATION statement for auto-execute '{}'", name);
                        }
                    }
                }
            }
        }

        // Only create target detection query if targetEvent is provided
        if (targetEvent != null) {
            handler.createDetectionQuery(StatementType.TARGET, name, targetEvent, safeTargetCondition, correlationCondition, relevantKeys, safeActivationCondition, safeTargetCondition);
        }

        handler.createFulfillmentQuery(name, correlationCondition, withinPeriod);
        handler.createTemporaryViolationQuery(name, correlationCondition, withinPeriod);
        handler.createPermanentViolationQuery(name, correlationCondition, withinPeriod);
    }

    private static Set<String> getRelevantKeys(CorrelationCondition correlationCondition, ConditionRequest safeActivationCondition, ConditionRequest safeTargetCondition) {
        Set<String> relevantKeys = new java.util.HashSet<>();
        if (EplQueryHelper.isConditionValid(safeActivationCondition)) {
            relevantKeys.add(safeActivationCondition.param());
        }
        if (EplQueryHelper.isConditionValid(safeTargetCondition)) {
            relevantKeys.add(safeTargetCondition.param());
        }
        if (correlationCondition != null && EplQueryHelper.isCorrelationValid(correlationCondition)) {
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
