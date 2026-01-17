package de.ur.service;

import de.ur.dao.*;
import de.ur.dto.AllowedTaskResponse;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static de.ur.dao.ConstraintStatus.*;
import static de.ur.dao.ConstraintType.*;

@ApplicationScoped
@Slf4j
public class AnalyzerService {
    private final Map<String, Map<String, String>> signalStates = new HashMap<>();

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
                List<String> actEvents = getEventsFromField(
                        constraint.getActivationEvent() != null ? constraint.getActivationEvent().name() : null);
                List<String> trgEvents = getEventsFromField(
                        constraint.getTargetEvent() != null ? constraint.getTargetEvent().name() : null);
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

                    List<String> actEvents = constraint.getActivationEvent() != null
                            ? getEventsFromField(constraint.getActivationEvent().name())
                            : Collections.emptyList();
                    if (actEvents.contains(event) && constraint.getActivationCondition() != null) {
                        var cond = constraint.getActivationCondition();
                        conditions.put(cond.param(), cond.operator() + " " + cond.value());
                    }

                    List<String> trgEvents = constraint.getTargetEvent() != null
                            ? getEventsFromField(constraint.getTargetEvent().name())
                            : Collections.emptyList();
                    if (trgEvents.contains(event) && constraint.getTargetCondition() != null) {
                        var cond = constraint.getTargetCondition();
                        conditions.put(cond.param(), cond.operator() + " " + cond.value());
                    }

                    log.debug("  [!] Event '{}' is unsafe: violates {} ({}). Conditions: {}", event,
                            constraint.getName(), constraint.getType(), conditions);
                    return AllowedTaskResponse.unsafe(event, conditions);
                }
            }

            log.debug("  [✓] Event '{}' is safe (no violations).", event);
            return AllowedTaskResponse.safe(event);
        }).collect(Collectors.toList());
    }

    public void updateSignalState(String signalName, Map<String, String> payload) {
        if (signalName != null && payload != null) {
            signalStates.put(signalName, new HashMap<>(payload));
            log.debug("Updated signal state for '{}': {}", signalName, payload);
        }
    }

    public Map<String, String> getSignalState(String signalName) {
        return signalStates.get(signalName);
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

    private boolean checkEventsWithConditions(List<Map<String, Object>> trace,
            List<String> eventsToFind,
            ConstraintCondition condition,
            Event eventInfo) {
        if (eventsToFind == null || eventsToFind.isEmpty()) {
            return false;
        }

        // If it's a signal event, check current signal state
        if (eventInfo != null && eventInfo.type() == Event.EventType.SIGNAL) {
            for (String eventName : eventsToFind) {
                Map<String, String> signalState = getSignalState(eventName);
                if (signalState != null) {
                    if (condition == null || matchesCondition(condition, signalState)) {
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
                    @SuppressWarnings("unchecked")
                    Map<String, String> payload = (Map<String, String>) event.get("payload");
                    if (payload == null || !matchesCondition(condition, payload)) {
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
            return true;
        }
        String actualValue = payload.get(condition.param());
        if (actualValue == null) {
            return false;
        }

        return switch (condition.operator()) {
            case "=" -> actualValue.equals(condition.value());
            case "!=" -> !actualValue.equals(condition.value());
            case "<" -> {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(condition.value());
                    yield actual < expected;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case ">" -> {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(condition.value());
                    yield actual > expected;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "<=" -> {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(condition.value());
                    yield actual <= expected;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case ">=" -> {
                try {
                    double actual = Double.parseDouble(actualValue);
                    double expected = Double.parseDouble(condition.value());
                    yield actual >= expected;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            default -> false;
        };
    }

    private ConstraintStatus getHypotheticalStatus(Constraint constraint, String event,
            List<Map<String, Object>> trace) {
        ConstraintType cType = constraint.getType();
        ConstraintStatus currentStatus = getStatusOrInit(constraint);

        List<String> actEvents = constraint.getActivationEvent() != null
                ? getEventsFromField(constraint.getActivationEvent().name())
                : Collections.emptyList();
        List<String> trgEvents = constraint.getTargetEvent() != null
                ? getEventsFromField(constraint.getTargetEvent().name())
                : Collections.emptyList();

        boolean actIsSignal = constraint.getActivationEvent() != null &&
                constraint.getActivationEvent().type() == Event.EventType.SIGNAL;
        boolean trgIsSignal = constraint.getTargetEvent() != null &&
                constraint.getTargetEvent().type() == Event.EventType.SIGNAL;

        boolean isAct = actEvents.contains(event);
        boolean isTrg = trgEvents.contains(event);
        boolean isDirectlyRelevant = isAct || isTrg;

        if (isDirectlyRelevant) {
            Map<String, String> payload = null;
            if (!trace.isEmpty()) {
                Map<String, Object> lastEvent = trace.getLast();
                @SuppressWarnings("unchecked")
                Map<String, String> temp = (Map<String, String>) lastEvent.get("payload");
                payload = temp;
            }

            if (isAct && constraint.getActivationCondition() != null) {
                if (actIsSignal) {
                    Map<String, String> signalState = getSignalState(constraint.getActivationEvent().name());
                    isAct = matchesCondition(constraint.getActivationCondition(), signalState);
                } else {
                    isAct = matchesCondition(constraint.getActivationCondition(), payload);
                }
            }
            if (isTrg && constraint.getTargetCondition() != null) {
                if (trgIsSignal) {
                    Map<String, String> signalState = getSignalState(constraint.getTargetEvent().name());
                    isTrg = matchesCondition(constraint.getTargetCondition(), signalState);
                } else {
                    isTrg = matchesCondition(constraint.getTargetCondition(), payload);
                }
            }

            isDirectlyRelevant = isAct || isTrg;
        }

        if (!isDirectlyRelevant) {
            if (TEMPORARY_VIOLATION.equals(currentStatus)
                    && (CHAIN_RESPONSE.equals(cType) || CHAIN_PRECEDENCE.equals(cType))) {
                log.debug("Irrelevant event '{}' causes violation in chain constraint {} ({})", event,
                        constraint.getName(), cType);
                return PERMANENT_VIOLATION;
            }
            return currentStatus;
        }

        return switch (cType) {
            case EXISTENCE -> isAct ? FULFILLED : currentStatus;

            case NOT_EXISTENCE -> isTrg ? PERMANENT_VIOLATION : currentStatus;

            case RESPONSE, ALTERNATE_RESPONSE -> {
                if (INIT.equals(currentStatus) && isAct) {
                    yield TEMPORARY_VIOLATION;
                }
                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (isTrg) {
                        if (ALTERNATE_RESPONSE.equals(cType)) {
                            boolean hasInterveningActivation = false;
                            for (int i = trace.size() - 1; i >= 0; i--) {
                                Map<String, Object> traceEvent = trace.get(i);
                                String eventType = (String) traceEvent.get("eventType");
                                if (actEvents.contains(eventType)) {
                                    if (constraint.getActivationCondition() != null) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, String> payload = (Map<String, String>) traceEvent.get("payload");
                                        if (payload == null
                                                || !matchesCondition(constraint.getActivationCondition(), payload)) {
                                            continue;
                                        }
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
                        yield PERMANENT_VIOLATION;
                    }
                    yield currentStatus;
                }
                yield currentStatus;
            }

            case RESPONDED_EXISTENCE -> {
                boolean seenAct = checkEventsWithConditions(trace, actEvents, constraint.getActivationCondition(),
                        constraint.getActivationEvent()) || isAct;
                boolean seenTrg = checkEventsWithConditions(trace, trgEvents, constraint.getTargetCondition(),
                        constraint.getTargetEvent()) || isTrg;
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
                        yield TEMPORARY_VIOLATION;
                    }
                    yield PERMANENT_VIOLATION;
                }
                yield currentStatus;
            }

            case PRECEDENCE, ALTERNATE_PRECEDENCE -> {
                if (isTrg) {
                    boolean hasMatchingActivation = false;
                    if (actIsSignal) {
                        if (constraint.getActivationCondition() != null) {
                            Map<String, String> signalState = getSignalState(constraint.getActivationEvent().name());
                            hasMatchingActivation = signalState != null
                                    && matchesCondition(constraint.getActivationCondition(), signalState);
                        } else {
                            hasMatchingActivation = getSignalState(constraint.getActivationEvent().name()) != null;
                        }
                    } else {
                        for (Map<String, Object> traceEvent : trace) {
                            String eventType = (String) traceEvent.get("eventType");
                            if (actEvents.contains(eventType)) {
                                if (constraint.getActivationCondition() != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, String> payload = (Map<String, String>) traceEvent.get("payload");
                                    if (payload != null
                                            && matchesCondition(constraint.getActivationCondition(), payload)) {
                                        hasMatchingActivation = true;
                                        break;
                                    }
                                } else {
                                    hasMatchingActivation = true;
                                    break;
                                }
                            }
                        }
                    }
                    yield hasMatchingActivation ? FULFILLED : PERMANENT_VIOLATION;
                }
                yield PERMANENT_VIOLATION;
            }

            case CHAIN_PRECEDENCE -> {
                if (isTrg) {
                    if (trace.isEmpty()) {
                        yield PERMANENT_VIOLATION;
                    }
                    Map<String, Object> lastEvent = trace.get(trace.size() - 1);
                    String lastEventType = (String) lastEvent.get("eventType");
                    if (actEvents.contains(lastEventType)) {
                        if (constraint.getActivationCondition() != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> payload = (Map<String, String>) lastEvent.get("payload");
                            if (payload == null || !matchesCondition(constraint.getActivationCondition(), payload)) {
                                yield PERMANENT_VIOLATION;
                            }
                        }
                        yield FULFILLED;
                    }
                    yield PERMANENT_VIOLATION;
                }
                yield currentStatus;
            }

            case NOT_RESPONSE -> {
                if (INIT.equals(currentStatus)) {
                    if (isAct) {
                        yield TEMPORARY_VIOLATION;
                    }
                    if (isTrg) {
                        boolean priorAct = checkEventsWithConditions(trace, actEvents,
                                constraint.getActivationCondition(), constraint.getActivationEvent());
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
                                constraint.getActivationCondition(), constraint.getActivationEvent());
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
