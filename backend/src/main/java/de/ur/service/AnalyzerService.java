package de.ur.service;

import de.ur.dao.Constraint;
import de.ur.dao.ConstraintCondition;
import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
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
    public Map<String, Object> checkFinishability(List<Constraint> constraints) {
        List<String> reasons = constraints.stream().filter(constraint -> {
            ConstraintStatus status = constraint.getStatus();
            return PERMANENT_VIOLATION.equals(status) || TEMPORARY_VIOLATION.equals(status);
        }).map(constraint -> {
            ConstraintStatus status = constraint.getStatus();
            String state = PERMANENT_VIOLATION.equals(status) ? "permanently" : "temporarily";
            return String.format("%s violated: %s (%s)", state, constraint.getName(), constraint.getType());
        }).toList();

        boolean canFinish = reasons.isEmpty();
        log.debug("Finishability check: canFinish={}, reasons.size={}", canFinish, reasons.size());
        return Map.of("canFinish", canFinish, "reasons", reasons);
    }

    public List<AllowedTaskResponse> analyzeAllowedTasks(List<Constraint> constraints, Set<String> possibleEvents, List<Map<String, Object>> trace) {
        if (constraints == null || possibleEvents == null || trace == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        return possibleEvents.stream().map(event -> {
            // Find all constraints that this event could potentially violate
            var relevantConstraints = constraints.stream().filter(constraint -> {
                ConstraintStatus currentStatus = getStatusOrInit(constraint);
                return !FULFILLED.equals(currentStatus) && !PERMANENT_VIOLATION.equals(currentStatus);
            }).filter(constraint -> {
                // Check if this event is relevant to the constraint
                List<String> actEvents = getEventsFromField(constraint.getActivationEvent());
                List<String> trgEvents = getEventsFromField(constraint.getTargetEvent());
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

                    // Check if it's an activation or target event and get the corresponding condition
                    List<String> actEvents = getEventsFromField(constraint.getActivationEvent());
                    if (actEvents.contains(event) && constraint.getActivationCondition() != null) {
                        var cond = constraint.getActivationCondition();
                        conditions.put(cond.param(), cond.operator() + " " + cond.value());
                    }

                    List<String> trgEvents = getEventsFromField(constraint.getTargetEvent());
                    if (trgEvents.contains(event) && constraint.getTargetCondition() != null) {
                        var cond = constraint.getTargetCondition();
                        conditions.put(cond.param(), cond.operator() + " " + cond.value());
                    }

                    log.debug("  [!] Event '{}' is unsafe: violates {} ({}). Conditions: {}", event, constraint.getName(), constraint.getType(), conditions);
                    return AllowedTaskResponse.unsafe(event, conditions);
                }
            }

            // If we get here, the event doesn't violate any constraints
            log.debug("  [✓] Event '{}' is safe (no violations).", event);
            return AllowedTaskResponse.safe(event);
        }).collect(Collectors.toList());
    }

    private ConstraintStatus getStatusOrInit(Constraint constraint) {
        return constraint.getStatus() != null ? constraint.getStatus() : INIT;
    }

    private List<String> getEventsFromField(String eventField) {
        if (eventField == null || eventField.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(eventField.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private boolean checkEventsWithConditions(List<Map<String, Object>> trace, List<String> eventsToFind, ConstraintCondition condition) {
        if (trace.isEmpty() || eventsToFind == null || eventsToFind.isEmpty()) {
            return false;
        }

        for (Map<String, Object> event : trace) {
            String eventType = (String) event.get("eventType");
            if (eventsToFind.contains(eventType)) {
                // If there's a condition, check if it matches the payload
                if (condition != null) {

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
            return true; // No condition means it matches by default
        }
        String actualValue = payload.get(condition.param());
        if (actualValue == null) {
            return false; // Required parameter not in payload
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

    private ConstraintStatus getHypotheticalStatus(Constraint constraint, String event, List<Map<String, Object>> trace) {
        ConstraintType cType = constraint.getType();
        ConstraintStatus currentStatus = getStatusOrInit(constraint);

        List<String> actEvents = getEventsFromField(constraint.getActivationEvent());
        List<String> trgEvents = getEventsFromField(constraint.getTargetEvent());

        // Check if the event matches the activation or target event type and conditions
        boolean isAct = actEvents.contains(event);
        boolean isTrg = trgEvents.contains(event);
        boolean isDirectlyRelevant = isAct || isTrg;

        // If we have conditions, we need to check if they match the event's payload
        if (isDirectlyRelevant && !trace.isEmpty()) {
            Map<String, Object> lastEvent = trace.getLast();
            Map<String, String> payload = (Map<String, String>) lastEvent.get("payload");

            if (isAct && constraint.getActivationCondition() != null) {
                isAct = matchesCondition(constraint.getActivationCondition(), payload);
            }
            if (isTrg && constraint.getTargetCondition() != null) {
                isTrg = matchesCondition(constraint.getTargetCondition(), payload);
            }

            isDirectlyRelevant = isAct || isTrg;
        }

        // Handle irrelevant events first
        if (!isDirectlyRelevant) {
            // For strict chain constraints in temporary state, irrelevant events cause violation
            if (TEMPORARY_VIOLATION.equals(currentStatus) && (CHAIN_RESPONSE.equals(cType) || CHAIN_PRECEDENCE.equals(cType))) {
                log.debug("Irrelevant event '{}' causes violation in chain constraint {} ({})", event, constraint.getName(), cType);
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
                                    if (constraint.getActivationCondition() != null) {
                                        Map<String, String> payload = (Map<String, String>) traceEvent.get("payload");
                                        if (payload == null || !matchesCondition(constraint.getActivationCondition(), payload)) {
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
                        // Alternate: no intervening activation (A) allowed
                        yield PERMANENT_VIOLATION;
                    }
                    yield currentStatus;
                }
                yield currentStatus;
            }

            case RESPONDED_EXISTENCE -> {
                // Compute based on events seen in trace + hypothetical event
                boolean seenAct = checkEventsWithConditions(trace, actEvents, constraint.getActivationCondition()) || isAct;
                boolean seenTrg = checkEventsWithConditions(trace, trgEvents, constraint.getTargetCondition()) || isTrg;
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
                    for (Map<String, Object> traceEvent : trace) {
                        String eventType = (String) traceEvent.get("eventType");
                        if (actEvents.contains(eventType)) {
                            // If there's an activation condition, check if it matches
                            if (constraint.getActivationCondition() != null) {
                                Map<String, String> payload = (Map<String, String>) traceEvent.get("payload");
                                if (payload != null && matchesCondition(constraint.getActivationCondition(), payload)) {
                                    hasMatchingActivation = true;
                                    break;
                                }
                            } else {
                                // No condition, any activation event matches
                                hasMatchingActivation = true;
                                break;
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
                        if (constraint.getActivationCondition() != null) {
                            Map<String, String> payload = (Map<String, String>) lastEvent.get("payload");
                            if (payload == null || !matchesCondition(constraint.getActivationCondition(), payload)) {
                                yield PERMANENT_VIOLATION;
                            }
                        }
                        yield FULFILLED;
                    }
                    yield PERMANENT_VIOLATION;
                }
                // Act event (A) is preparatory, no state change; handled via irrelevant for chains
                yield currentStatus;
            }

            case NOT_RESPONSE -> {
                if (INIT.equals(currentStatus)) {
                    if (isAct) {
                        yield TEMPORARY_VIOLATION;
                    }
                    if (isTrg) {
                        boolean priorAct = checkEventsWithConditions(trace, actEvents, constraint.getActivationCondition());
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
                        boolean priorAct = checkEventsWithConditions(trace, actEvents, constraint.getActivationCondition());
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
