package de.ur.service;

import de.ur.dao.*;
import de.ur.dto.AllowedTaskResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static de.ur.dao.ConstraintStatus.*;
import static de.ur.dao.ConstraintType.*;

@ApplicationScoped
@Slf4j
public class AnalyzerService {
    @Inject
    ConstraintService constraintService;

    private final Map<String, Map<String, String>> signalStates = new HashMap<>();
    
    // Tracks when a signal condition was first met for a given constraint
    // Key: "constraintName:signalName" -> timestamp when condition was first met (null if not met)
    private final Map<String, Long> signalConditionMetSince = new HashMap<>();

    public Map<String, Object> checkFinishability(List<Constraint> constraints) {
        List<String> reasons = constraints.parallelStream()
                .filter(constraint -> {
                    // For NOT_EXISTENCE and NOT_RESPONSE, only PERMANENT_VIOLATION blocks
                    // completion
                    if (NOT_EXISTENCE.equals(constraint.getType()) || NOT_RESPONSE.equals(constraint.getType())) {
                        return PERMANENT_VIOLATION.equals(constraint.getStatus());
                    }
                    // For all other constraints, both violations block completion
                    ConstraintStatus status = constraint.getStatus();
                    return PERMANENT_VIOLATION.equals(status) || TEMPORARY_VIOLATION.equals(status);
                })
                .map(constraint -> {
                    ConstraintStatus status = constraint.getStatus();
                    String state = PERMANENT_VIOLATION.equals(status) ? "permanently" : "temporarily";
                    return String.format("%s violated: %s (%s)", state, constraint.getName(), constraint.getType());
                })
                .toList();

        boolean canFinish = reasons.isEmpty();
        log.debug("Finishability check: canFinish={}, reasons.size={}", canFinish, reasons.size());
        return Map.of("canFinish", canFinish, "reasons", reasons);
    }

    public List<AllowedTaskResponse> analyzeAllowedTasks(List<Constraint> constraints, Set<String> possibleEvents,
            List<Map<String, Object>> trace) {
        if (constraints == null || possibleEvents == null || trace == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        return possibleEvents.parallelStream().map(event -> {
            // Find all constraints that this event could potentially violate
            var relevantConstraints = constraints.parallelStream().filter(constraint -> {
                ConstraintStatus currentStatus = getStatusOrInit(constraint);
                return !FULFILLED.equals(currentStatus) && !PERMANENT_VIOLATION.equals(currentStatus);
            }).filter(constraint -> {
                // Check if this event is relevant to the constraint
                List<String> actEvents = getEventNames(constraint.getActivationEvent());
                List<String> trgEvents = getEventNames(constraint.getTargetEvent());
                return actEvents.contains(event) || trgEvents.contains(event);
            }).toList();

            // If no constraints apply, the event is safe
            if (relevantConstraints.isEmpty()) {
                log.debug("  [✓] Event '{}' is safe (no constraints apply).", event);
                return AllowedTaskResponse.safe(event);
            }

            // Check each constraint to see if it would be violated
            for (var constraint : relevantConstraints) {
                ConstraintStatus status = getHypotheticalStatus(constraint, event, trace);
                if (PERMANENT_VIOLATION.equals(status)) {
                    // This event would violate the constraint - collect condition details
                    Map<String, String> conditions = new HashMap<>();

                    // Check if it's an activation or target event and get the corresponding
                    // condition
                    Event activationEvent = constraint.getActivationEvent();
                    List<String> actEvents = getEventNames(activationEvent);
                    if (actEvents.contains(event) && constraint.getActivationCondition() != null) {
                        var cond = constraint.getActivationCondition();
                        conditions.put(cond.param(), cond.operator() + " " + cond.value());
                    }

                    Event targetEvent = constraint.getTargetEvent();
                    List<String> trgEvents = getEventNames(targetEvent);
                    if (trgEvents.contains(event) && constraint.getTargetCondition() != null) {
                        var cond = constraint.getTargetCondition();
                        conditions.put(cond.param(), cond.operator() + " " + cond.value());
                    }

                    log.debug("  [!] Event '{}' is unsafe: violates {} ({}). Conditions: {}", event,
                            constraint.getName(), constraint.getType(), conditions);
                    return AllowedTaskResponse.unsafe(event, conditions);
                }
            }

            // If we get here, the event doesn't violate any constraints
            log.debug("  [✓] Event '{}' is safe (no violations).", event);
            return AllowedTaskResponse.safe(event);
        }).collect(Collectors.toList());
    }

    public void updateSignalState(String signalName, Map<String, String> payload) {
        if (signalName != null && payload != null) {
            signalStates.put(signalName, new HashMap<>(payload));
            log.debug("Updated signal state for '{}': {}", signalName, payload);
            updateSignalConditionTracking(signalName, payload);
        }
    }

    private void updateSignalConditionTracking(String signalName, Map<String, String> payload) {
        long now = System.currentTimeMillis();
        
        for (Constraint constraint : constraintService.getConstraints().values()) {
            Event activationEvent = constraint.getActivationEvent();
            if (activationEvent != null && 
                activationEvent.type() == Event.EventType.SIGNAL &&
                signalName.equals(activationEvent.name())) {
                
                ConstraintCondition condition = constraint.getActivationCondition();
                String trackingKey = constraint.getName() + ":activation:" + signalName;
                
                if (condition != null && condition.timer() != null && condition.timer() > 0) {
                    boolean conditionMet = matchesCondition(condition, payload);
                    
                    if (conditionMet) {
                        if (!signalConditionMetSince.containsKey(trackingKey)) {
                            signalConditionMetSince.put(trackingKey, now);
                            log.debug("Signal condition first met for {}: tracking started at {}", trackingKey, now);
                        }
                    } else {
                        if (signalConditionMetSince.containsKey(trackingKey)) {
                            signalConditionMetSince.remove(trackingKey);
                            log.debug("Signal condition no longer met for {}: tracking reset", trackingKey);
                        }
                    }
                }
            }
            
            Event targetEvent = constraint.getTargetEvent();
            if (targetEvent != null && 
                targetEvent.type() == Event.EventType.SIGNAL &&
                signalName.equals(targetEvent.name())) {
                
                ConstraintCondition condition = constraint.getTargetCondition();
                String trackingKey = constraint.getName() + ":target:" + signalName;
                
                if (condition != null && condition.timer() != null && condition.timer() > 0) {
                    boolean conditionMet = matchesCondition(condition, payload);
                    
                    if (conditionMet) {
                        if (!signalConditionMetSince.containsKey(trackingKey)) {
                            signalConditionMetSince.put(trackingKey, now);
                            log.debug("Signal condition first met for {}: tracking started at {}", trackingKey, now);
                        }
                    } else {
                        if (signalConditionMetSince.containsKey(trackingKey)) {
                            signalConditionMetSince.remove(trackingKey);
                            log.debug("Signal condition no longer met for {}: tracking reset", trackingKey);
                        }
                    }
                }
            }
        }
    }

    public Map<String, String> getSignalState(String signalName) {
        return signalStates.get(signalName);
    }

    public Map<String, Map<String, String>> getAllSignalStates() {
        return new HashMap<>(signalStates);
    }

    public boolean isSignalTimerConditionMet(String constraintName, String signalName, 
            ConstraintCondition condition, boolean isActivation) {
        if (condition == null || condition.timer() == null || condition.timer() <= 0) {
            return true;
        }
        
        String trackingKey = constraintName + ":" + (isActivation ? "activation" : "target") + ":" + signalName;
        Long conditionMetSince = signalConditionMetSince.get(trackingKey);
        
        if (conditionMetSince == null) {
            log.debug("Signal timer condition NOT met for {}: condition not currently satisfied", trackingKey);
            return false;
        }
        
        long now = System.currentTimeMillis();
        long requiredDelay = condition.timer() * 1000;
        long elapsed = now - conditionMetSince;
        
        boolean timerMet = elapsed >= requiredDelay;
        log.debug("Signal timer check for {}: timer={}s, elapsed={}ms, required={}ms, met={}", 
                trackingKey, condition.timer(), elapsed, requiredDelay, timerMet);
        
        return timerMet;
    }

    public void resetSignalTracking() {
        signalStates.clear();
        signalConditionMetSince.clear();
        log.debug("Signal tracking state has been reset");
    }

    private ConstraintStatus getStatusOrInit(Constraint constraint) {
        return constraint.getStatus() != null ? constraint.getStatus() : INIT;
    }

    private List<String> getEventsFromField(String eventField) {
        if (eventField == null || eventField.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(eventField.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Gets event names from an Event object.
     */
    private List<String> getEventNames(Event event) {
        if (event == null || event.name() == null) {
            return Collections.emptyList();
        }
        return List.of(event.name());
    }

    private boolean checkEventsWithConditions(List<Map<String, Object>> trace,
            List<String> eventsToFind,
            ConstraintCondition condition,
            Event eventInfo,
            String constraintName,
            boolean isActivation) {
        if (eventsToFind == null || eventsToFind.isEmpty()) {
            return false;
        }

        // If it's a signal event, check current signal state
        if (eventInfo != null && eventInfo.type() == Event.EventType.SIGNAL) {
            for (String eventName : eventsToFind) {
                Map<String, String> signalState = getSignalState(eventName);
                if (signalState != null) {
                    if (condition == null || 
                       (matchesCondition(condition, signalState) && 
                        isSignalTimerConditionMet(constraintName, eventName, condition, isActivation))) {
                        return true;
                    }
                }
            }
            return false;
        }

        // For task events, check the trace
        if (trace.isEmpty()) {
            return false;
        }

        for (Map<String, Object> event : trace) {
            String eventType = (String) event.get("eventType");
            if (eventsToFind.contains(eventType)) {
                if (condition != null) {
                    if (!isEventValid(event, condition)) {
                        continue;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean matchesCondition(ConstraintCondition condition, Map<String, String> payload) {
        if (condition == null || payload == null) {
            return true; // No condition means it matches by default
        }
        String actualValue = payload.get(condition.param());
        if (actualValue == null) {
            return false; // Required parameter not in payload
        }

        return switch (condition.operator()) {
            case "=" -> actualValue.equals(condition.value());
            case "==" -> actualValue.equals(condition.value());
            case "!=" -> !actualValue.equals(condition.value());
            case "<" -> {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(condition.value());
                    yield actual < expected;
                } catch (NumberFormatException e) {
                    yield false; // Type mismatch
                }
            }
            case ">" -> {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(condition.value());
                    yield actual > expected;
                } catch (NumberFormatException e) {
                    yield false; // Type mismatch
                }
            }
            case "<=" -> {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(condition.value());
                    yield actual <= expected;
                } catch (NumberFormatException e) {
                    yield false; // Type mismatch
                }
            }
            case ">=" -> {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(condition.value());
                    yield actual >= expected;
                } catch (NumberFormatException e) {
                    yield false; // Type mismatch
                }
            }
            default -> false; // Unknown operator
        };
    }

    private boolean isEventValid(Map<String, Object> event, ConstraintCondition condition) {
        if (condition == null) {
            return true;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) event.get("payload");
        if (!matchesCondition(condition, payload)) {
            return false;
        }

        if (condition.timer() != null && condition.timer() > 0) {
            Object tsObj = event.get("timestamp");
            if (tsObj instanceof Number) {
                long eventTime = ((Number) tsObj).longValue();
                long now = System.currentTimeMillis();
                long requiredDelay = condition.timer() * 1000;
                long elapsed = now - eventTime;
                
                log.debug("Timer Check: eventType={}, timer={}s, elapsed={}ms, required={}ms", 
                        event.get("eventType"), condition.timer(), elapsed, requiredDelay);

                if (elapsed < requiredDelay) {
                    log.debug("  -> Timer NOT met.");
                    return false;
                }
                log.debug("  -> Timer met.");
            } else {
                log.warn("Timer present but no timestamp in event: {}", event);
                return false;
            }
        }
        
        return true;
    }

    private ConstraintStatus getHypotheticalStatus(Constraint constraint, String event,
            List<Map<String, Object>> trace) {
        ConstraintType cType = constraint.getType();
        ConstraintStatus currentStatus = getStatusOrInit(constraint);

        List<String> actEvents = getEventNames(constraint.getActivationEvent());
        List<String> trgEvents = getEventNames(constraint.getTargetEvent());

        // Determine if events are signals or tasks
        boolean actIsSignal = constraint.getActivationEvent() != null &&
                constraint.getActivationEvent().type() == Event.EventType.SIGNAL;
        boolean trgIsSignal = constraint.getTargetEvent() != null &&
                constraint.getTargetEvent().type() == Event.EventType.SIGNAL;

        // Check if the event matches the activation or target event type and conditions
        boolean isAct = actEvents.contains(event);
        boolean isTrg = trgEvents.contains(event);
        boolean isDirectlyRelevant = isAct || isTrg;

        // Check conditions for the hypothetical event
        // For signal events, check current signal state
        // For task events, check the payload from the last event in trace
        if (isDirectlyRelevant) {
            Map<String, String> payload = null;
            if (!trace.isEmpty()) {
                Map<String, Object> lastEvent = trace.getLast();
                @SuppressWarnings("unchecked")
                Map<String, String> temp = (Map<String, String>) lastEvent.get("payload");
                payload = temp;
            }

            if (isAct && constraint.getActivationCondition() != null) {
                // For signals, check current state; for tasks, check payload
                if (actIsSignal) {
                    Map<String, String> signalState = getSignalState(constraint.getActivationEvent().name());
                    isAct = matchesCondition(constraint.getActivationCondition(), signalState)
                            && isSignalTimerConditionMet(constraint.getName(), constraint.getActivationEvent().name(), constraint.getActivationCondition(), true);
                } else {
                    isAct = matchesCondition(constraint.getActivationCondition(), payload);
                }
            }
            if (isTrg && constraint.getTargetCondition() != null) {
                // For signals, check current state; for tasks, check payload
                if (trgIsSignal) {
                    Map<String, String> signalState = getSignalState(constraint.getTargetEvent().name());
                    isTrg = matchesCondition(constraint.getTargetCondition(), signalState)
                            && isSignalTimerConditionMet(constraint.getName(), constraint.getTargetEvent().name(), constraint.getTargetCondition(), false);
                } else {
                    isTrg = matchesCondition(constraint.getTargetCondition(), payload);
                }
            }

            isDirectlyRelevant = isAct || isTrg;
        }

        // Handle irrelevant events first
        if (!isDirectlyRelevant) {
            // For strict chain constraints in temporary state, irrelevant events cause
            // violation
            if (TEMPORARY_VIOLATION.equals(currentStatus)
                    && (CHAIN_RESPONSE.equals(cType) || CHAIN_PRECEDENCE.equals(cType))) {
                log.debug("Irrelevant event '{}' causes violation in chain constraint {} ({})", event,
                        constraint.getName(), cType);
                return PERMANENT_VIOLATION;
            }
            // For other constraints, status unchanged
            return currentStatus;
        }

        // Type-specific status updates
        return switch (cType) {
            case EXISTENCE -> isAct ? FULFILLED : currentStatus;

            case NOT_EXISTENCE -> isTrg ? PERMANENT_VIOLATION : currentStatus;

            case RESPONSE, ALTERNATE_RESPONSE -> {
                if (INIT.equals(currentStatus) && isAct) {
                    yield TEMPORARY_VIOLATION;
                }
                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (isTrg) {
                        // For RESPONSE, any target event is fine
                        // For ALTERNATE_RESPONSE, need to check if there was an activation in between
                        if (ALTERNATE_RESPONSE.equals(cType)) {
                            // Check if there was an activation since the last target
                            boolean hasInterveningActivation = false;
                            for (int i = trace.size() - 1; i >= 0; i--) {
                                Map<String, Object> traceEvent = trace.get(i);
                                String eventType = (String) traceEvent.get("eventType");
                                if (actEvents.contains(eventType)) {
                                    if (!isEventValid(traceEvent, constraint.getActivationCondition())) {
                                        continue;
                                    }
                                    hasInterveningActivation = true;
                                    break;
                                }
                                if (trgEvents.contains(eventType)) {
                                    break;
                                }
                            }
                            if (hasInterveningActivation) {
                                yield PERMANENT_VIOLATION;
                            }
                        }
                        yield FULFILLED;
                    }
                    if (ALTERNATE_RESPONSE.equals(cType) && isAct) {
                        // Alternate: no intervening activation (A) allowed
                        yield PERMANENT_VIOLATION;
                    }
                    yield currentStatus;
                }
                yield currentStatus;
            }

            case RESPONDED_EXISTENCE -> {
                // Compute based on events seen in trace + hypothetical event
                boolean seenAct = checkEventsWithConditions(trace, actEvents, constraint.getActivationCondition(),
                        constraint.getActivationEvent(), constraint.getName(), true) || isAct;
                boolean seenTrg = checkEventsWithConditions(trace, trgEvents, constraint.getTargetCondition(),
                        constraint.getTargetEvent(), constraint.getName(), false) || isTrg;
                if (FULFILLED.equals(currentStatus) || PERMANENT_VIOLATION.equals(currentStatus)) {
                    yield currentStatus;
                }
                if (seenAct && seenTrg) {
                    yield FULFILLED;
                } else if (seenAct || seenTrg) {
                    yield TEMPORARY_VIOLATION;
                } else {
                    yield INIT;
                }
            }

            case CHAIN_RESPONSE -> {
                if (INIT.equals(currentStatus) && isAct) {
                    yield TEMPORARY_VIOLATION;
                }
                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (isTrg) {
                        yield FULFILLED;
                    }
                    if (isAct) {
                        yield TEMPORARY_VIOLATION; // Allow re-activation in chain
                    }
                    // Handled in irrelevant block above for non-relevant
                    yield PERMANENT_VIOLATION;
                }
                yield currentStatus;
            }

            case PRECEDENCE, ALTERNATE_PRECEDENCE -> {
                if (isTrg) {
                    // Check if there's a matching activation event with conditions
                    boolean hasMatchingActivation = false;

                    // For signal-based activation events, check current signal state
                    if (actIsSignal) {
                        if (constraint.getActivationCondition() != null) {
                            Map<String, String> signalState = getSignalState(constraint.getActivationEvent().name());
                            hasMatchingActivation = signalState != null
                                    && matchesCondition(constraint.getActivationCondition(), signalState)
                                    && isSignalTimerConditionMet(constraint.getName(), 
                                            constraint.getActivationEvent().name(),
                                            constraint.getActivationCondition(), true);
                        } else {
                            // No condition, check if signal exists
                            hasMatchingActivation = getSignalState(constraint.getActivationEvent().name()) != null;
                        }
                    } else {
                        // For task-based activation events, check the trace
                        for (Map<String, Object> traceEvent : trace) {
                            String eventType = (String) traceEvent.get("eventType");
                            if (actEvents.contains(eventType)) {
                                if (isEventValid(traceEvent, constraint.getActivationCondition())) {
                                    hasMatchingActivation = true;
                                    break;
                                }
                            }
                        }
                    }

                    // If we found a matching activation, the constraint is fulfilled
                    // Otherwise, it's a violation
                    yield hasMatchingActivation ? FULFILLED : PERMANENT_VIOLATION;
                }
                // For activation events, we don't change the state
                yield currentStatus;
            }

            case CHAIN_PRECEDENCE -> {
                if (isTrg) {
                    // Chain: B must be immediately after A
                    if (trace.isEmpty()) {
                        yield PERMANENT_VIOLATION;
                    }
                    Map<String, Object> lastEvent = trace.get(trace.size() - 1);
                    String lastEventType = (String) lastEvent.get("eventType");
                    if (actEvents.contains(lastEventType)) {
                        // Check condition if present
                        if (!isEventValid(lastEvent, constraint.getActivationCondition())) {
                            yield PERMANENT_VIOLATION;
                        }
                        yield FULFILLED;
                    }
                    yield PERMANENT_VIOLATION;
                }
                // Act event (A) is preparatory, no state change; handled via irrelevant for
                // chains
                yield currentStatus;
            }

            case NOT_RESPONSE -> {
                if (INIT.equals(currentStatus)) {
                    if (isAct) {
                        yield TEMPORARY_VIOLATION;
                    }
                    if (isTrg) {
                        boolean priorAct = checkEventsWithConditions(trace, actEvents,
                                constraint.getActivationCondition(), constraint.getActivationEvent(),
                                constraint.getName(), true);
                        yield priorAct ? PERMANENT_VIOLATION : currentStatus;
                    }
                }
                if (TEMPORARY_VIOLATION.equals(currentStatus) && isTrg) {
                    yield PERMANENT_VIOLATION;
                }
                yield currentStatus;
            }

            case NOT_PRECEDENCE -> {
                if (INIT.equals(currentStatus)) {
                    if (isTrg) {
                        boolean priorAct = checkEventsWithConditions(trace, actEvents,
                                constraint.getActivationCondition(), constraint.getActivationEvent(),
                                constraint.getName(), true);
                        // Violation if B is preceded by A (forbids precedence)
                        yield priorAct ? PERMANENT_VIOLATION : FULFILLED;
                    }
                    if (isAct) {
                        yield TEMPORARY_VIOLATION;
                    }
                }
                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (isTrg) {
                        yield PERMANENT_VIOLATION;
                    }
                    if (isAct) {
                        yield TEMPORARY_VIOLATION;
                    }
                }
                yield currentStatus;
            }
            default -> currentStatus;
        };
    }
}
