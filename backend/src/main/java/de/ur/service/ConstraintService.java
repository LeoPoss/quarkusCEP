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

    @Inject
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Inject
    jakarta.enterprise.inject.Instance<TaskExecutorService> taskExecutorServiceInstance;

    @Getter
    private final List<Map<String, Object>> executionLogs = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void logExecution(String type, String constraintName, String targetTask, String message, Map<String, String> payload) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", System.currentTimeMillis());
        logEntry.put("type", type);
        logEntry.put("constraintName", constraintName);
        logEntry.put("targetTask", targetTask);
        logEntry.put("message", message);
        logEntry.put("payload", payload != null ? new HashMap<>(payload) : null);
        executionLogs.add(logEntry);
    }

    private Map<String, String> parseAndInterpolatePayload(String payloadStr, Map<String, String> triggerPayload) {
        Map<String, String> result = new HashMap<>();
        if (payloadStr == null || payloadStr.isBlank()) {
            return result;
        }

        String interpolated = payloadStr;
        if (triggerPayload != null) {
            for (Map.Entry<String, String> entry : triggerPayload.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                interpolated = interpolated.replace(placeholder, entry.getValue() != null ? entry.getValue() : "");
            }
        }
        interpolated = interpolated.replace("${timestamp}", String.valueOf(System.currentTimeMillis()));

        // Try JSON
        try {
            Map<String, Object> map = objectMapper.readValue(interpolated, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
            return result;
        } catch (Exception e) {
            log.debug("Failed to parse payload as JSON, falling back to comma-separated format: {}", e.getMessage());
        }

        // Try comma-separated
        String[] pairs = interpolated.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            } else if (kv.length == 1 && !kv[0].isBlank()) {
                result.put(kv[0].trim(), "");
            }
        }
        return result;
    }

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

    public void setupConstraint(ConstraintType type, String name, Long withinPeriod, String activationEventName, ConditionRequest activationCondition, String targetEventName, ConditionRequest targetCondition, CorrelationCondition correlationCondition, ConstraintStatus status, String activationEventType, String targetEventType, boolean autoExecute, String autoExecutePayload) {
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
        Constraint constraint = new Constraint(name, withinPeriod != null ? withinPeriod : null, new ArrayList<>(), activationEvent, EplQueryHelper.isConditionValid(safeActivationCondition) ? new ConstraintCondition(safeActivationCondition.param(), safeActivationCondition.operator(), safeActivationCondition.value(), safeActivationCondition.timer()) : null, targetEvent, EplQueryHelper.isConditionValid(safeTargetCondition) ? new ConstraintCondition(safeTargetCondition.param(), safeTargetCondition.operator(), safeTargetCondition.value(), safeTargetCondition.timer()) : null, correlationCondition, type, status, autoExecute, autoExecutePayload);
        constraints.put(name, constraint);

        var handler = constraintHandlerFactory.getHandler(type);

        // Only create activation detection query if activationEvent is provided
        if (activationEvent != null) {
            handler.createDetectionQuery(StatementType.ACTIVATION, name, activationEvent, safeActivationCondition, correlationCondition, relevantKeys, safeActivationCondition, safeTargetCondition);

            // Auto-execute: when activation fires, automatically inject the target event
            if (autoExecute && targetEvent != null) {
                log.info("Setting up auto-execute for '{}': target='{}'", name, targetEvent.name());
                final String targetName = targetEvent.name();
                // Find the deployed ACTIVATION statement and attach a listener directly
                for (var s : constraint.getEplStatements()) {
                    if (s.type() == StatementType.ACTIVATION) {
                        var stmt = esperService.getRuntime().getDeploymentService()
                            .getStatement(s.deploymentId(), name + "_activation");
                        if (stmt != null) {
                            stmt.addListener((newEvents, oldEvents, statement, runtime) -> {
                                if (newEvents != null && newEvents.length > 0) {
                                    // Extract trigger payload
                                    Map<String, String> triggerPayload = new HashMap<>();
                                    try {
                                        Object underlying = newEvents[0].getUnderlying();
                                        if (underlying instanceof Map<?, ?> map) {
                                            if (map.containsKey("t1")) {
                                                Object t1Val = map.get("t1");
                                                if (t1Val instanceof GenericEvent ge) {
                                                    triggerPayload = ge.getPayload();
                                                } else if (t1Val instanceof Map<?, ?> t1Map) {
                                                    for (Map.Entry<?, ?> e : t1Map.entrySet()) {
                                                        if (e.getValue() != null) triggerPayload.put(e.getKey().toString(), e.getValue().toString());
                                                    }
                                                }
                                            } else if (map.containsKey("payload")) {
                                                Object pVal = map.get("payload");
                                                if (pVal instanceof Map<?, ?> pMap) {
                                                    for (Map.Entry<?, ?> e : pMap.entrySet()) {
                                                        if (e.getValue() != null) triggerPayload.put(e.getKey().toString(), e.getValue().toString());
                                                    }
                                                }
                                            }
                                        } else if (underlying instanceof GenericEvent ge) {
                                            triggerPayload = ge.getPayload();
                                        }
                                    } catch (Exception ex) {
                                        log.warn("Failed to extract trigger event payload", ex);
                                    }

                                    // Parse and interpolate target payload
                                    Map<String, String> targetPayload = parseAndInterpolatePayload(
                                            constraint.getAutoExecutePayload(), triggerPayload);

                                    // Add source=autoexecute and constraint name if not present
                                    targetPayload.putIfAbsent("source", "autoexecute");
                                    targetPayload.putIfAbsent("constraint", name);

                                    // If target condition param has been set in request, also make sure it exists
                                    if (safeTargetCondition != null && safeTargetCondition.param() != null && !safeTargetCondition.param().isBlank()) {
                                        targetPayload.putIfAbsent(safeTargetCondition.param(), safeTargetCondition.value());
                                    }

                                    log.info("Auto-execute triggered for '{}': injecting '{}' with payload {}",
                                        name, targetName, targetPayload);

                                    logExecution("TRIGGER", name, targetName, "Constraint auto-execution triggered.", targetPayload);
                                    
                                    try {
                                        taskExecutorServiceInstance.get().executeTask(targetName, name, targetPayload, true);
                                    } catch (Exception ex) {
                                        log.error("Failed to execute task worker", ex);
                                    }
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
