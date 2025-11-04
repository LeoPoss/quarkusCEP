package de.ur.service;

import de.ur.dao.Constraint;
import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static de.ur.dao.ConstraintStatus.*;
import static de.ur.dao.ConstraintType.CHAIN_RESPONSE;

@ApplicationScoped
@Slf4j
public class AnalyzerService {

    public Map<String, Object> checkFinishability(List<Constraint> constraints) {
        List<String> reasons = new ArrayList<>();

        for (Constraint constraint : constraints) {
            ConstraintStatus status = constraint.getStatus();

            switch (status) {
                case PERMANENT_VIOLATION:
                    // Condition 1: Cannot finish if any constraint has failed.
                    reasons.add(String.format("Failed: %s (%s) is permanently violated.",
                            constraint.getName(), constraint.getType()));
                    break;

                case TEMPORARY_VIOLATION:
                    // Condition 2: Cannot finish if an obligation is actively pending.
                    // (e.g., RESPONSE waiting for target, PRECEDENCE in timer window)
                    reasons.add(String.format("Pending: %s (%s) is in a temporary violation state.",
                            constraint.getName(), constraint.getType()));
                    break;

// TODO EXISTENCE is always initally violated to prevent this
//                case INIT:
//                    // Condition 3: Cannot finish if a *mandatory* constraint has not been met.
//                    // Most INIT constraints are fine (e.g., RESPONSE 'A' never happened).
//                    // But EXISTENCE *must* happen.
//                    if (constraint.getType() == EXISTENCE) {
//                        reasons.add(String.format("Pending: %s (%s) must still occur.",
//                                constraint.getName(), constraint.getActivationEvent()));
//                    }
//                    break;

                case FULFILLED:
                default:
                    // FULFILLED is good.
                    // Other states are considered "finishable".
                    break;
            }
        }

        return Map.of(
                "canFinish", reasons.isEmpty(),
                "reasons", reasons
        );
    }

    public Map<String, Boolean> analyzeAllowedTasks(List<Constraint> constraints, Set<String> possibleEvents, List<String> trace) {
        Map<String, Boolean> analysis = new HashMap<>();

        for (String event : possibleEvents) {
            boolean isUnsafe = false;

            for (Constraint constraint : constraints) {
                // Skip constraints that are already finished
                ConstraintStatus currentStatus = constraint.getStatus();
                if (FULFILLED.equals(currentStatus) || PERMANENT_VIOLATION.equals(currentStatus)) {
                    continue;
                }

                ConstraintStatus hypotheticalStatus = getHypotheticalStatus(constraint, event, trace);
                if (PERMANENT_VIOLATION.equals(hypotheticalStatus)) {
                    log.debug("  [!] Task '{}' is UNSAFE. Violates: {} ({})",
                            event, constraint.getName(), constraint.getType());
                    isUnsafe = true;
                    break; // One violation is enough to mark the event unsafe
                }
            }

            analysis.put(event, isUnsafe);
            if (!isUnsafe) {
                log.debug("  [✓] Task '{}' is SAFE.", event);
            }
        }
        return analysis;
    }

    private List<String> getEventsFromField(String eventField) {
        if (eventField == null || eventField.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(eventField.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private boolean traceContainsAny(List<String> trace, List<String> eventsToFind) {
        if (eventsToFind == null || eventsToFind.isEmpty() || trace == null || trace.isEmpty()) {
            return false;
        }
        return trace.stream().anyMatch(eventsToFind::contains);
    }

    private ConstraintStatus getHypotheticalStatus(Constraint constraint, String event, List<String> trace) {
        ConstraintType cType = constraint.getType();
        ConstraintStatus currentStatus = constraint.getStatus() != null ? constraint.getStatus() : INIT;

        List<String> actEvents = getEventsFromField(constraint.getActivationEvent());
        List<String> trgEvents = getEventsFromField(constraint.getTargetEvent());

        boolean isDirectlyRelevant = actEvents.contains(event) || trgEvents.contains(event);

        if (!isDirectlyRelevant) {
            // Event is not A or C.
            // Is it an active CHAIN_RESPONSE? If so, it's a potential violation.
            if (cType == CHAIN_RESPONSE && TEMPORARY_VIOLATION.equals(currentStatus)) {
                // This is Event 'B'. We must let the switch logic handle it.
            } else {
                // For all other constraints, this event is truly irrelevant.
                return currentStatus;
            }
        }

        switch (cType) {
            // --- Existence ---
            case EXISTENCE:
                if (actEvents.contains(event)) return FULFILLED;
                break;

            case NOT_EXISTENCE:
                if (trgEvents.contains(event)) return PERMANENT_VIOLATION;
                break;

            // --- Response Family ---
            case RESPONSE:
            case ALTERNATE_RESPONSE:
                if (INIT.equals(currentStatus) && actEvents.contains(event))
                    return TEMPORARY_VIOLATION;
                if (TEMPORARY_VIOLATION.equals(currentStatus) && trgEvents.contains(event))
                    return FULFILLED;
                break;

            case RESPONDED_EXISTENCE:
                // This logic works both ways (past or future)
                if (INIT.equals(currentStatus)) {
                    if (actEvents.contains(event)) { // Event 'A' happens
                        if (traceContainsAny(trace, trgEvents)) { // Checks if 'B' is in trace
                            // 'B' is in trace, net result is FULFILLED
                            return FULFILLED;
                        } else {
                            // 'B' is not in trace, net result is TEMPORARY_VIOLATION
                            return TEMPORARY_VIOLATION;
                        }
                    }
                }

                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    // This status means 'A' happened, and we are waiting for 'B'.
                    if (trgEvents.contains(event)) {
                        return FULFILLED;
                    }
                    // Another 'A' just re-triggers the violation state.
                    if (actEvents.contains(event)) {
                        return TEMPORARY_VIOLATION;
                    }
                }
                break;

            case CHAIN_RESPONSE:
                if (INIT.equals(currentStatus)) {
                    if (actEvents.contains(event))
                        return TEMPORARY_VIOLATION;

                } else if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    // We are in the "immediate" window, waiting for Target 'C'.

                    // 1. Is it the Target ('C')?
                    if (trgEvents.contains(event)) {
                        return FULFILLED; // SAFE
                    }

                    // 2. Is it another Activation ('A')?
                    if (actEvents.contains(event)) {
                        return TEMPORARY_VIOLATION; // SAFE (re-activates)
                    }

                    // 3. Is it anything else? ('B', 'D', etc.)
                    // This is the chain-breaking violation.
                    return PERMANENT_VIOLATION; // UNSAFE
                }
                break;

            case PRECEDENCE:
            case ALTERNATE_PRECEDENCE:
                if (INIT.equals(currentStatus)) {
                    if (trgEvents.contains(event)) {
                        // --- THIS IS THE NEW TRACE-AWARE LOGIC ---
                        // This is Event 'B' (target). We must check the trace for 'A' (activation).
                        boolean activationEventExists = traceContainsAny(trace, actEvents);

                        if (activationEventExists) {
                            // 'A' IS in the trace. Event 'B' will trigger
                            // the fulfillment query [A -> B].
                            return FULFILLED; // This is SAFE
                        } else {
                            // 'A' IS NOT in the trace. Event 'B' will trigger
                            // your temporary violation query and start the 1-sec timer.
                            // We pessimistically treat this impending violation as an immediate UNSAFE event.
                            return PERMANENT_VIOLATION;
                        }
                    }
                    if (actEvents.contains(event)) {
                        // This is Event 'A'. This is always safe for Precedence
                        // and doesn't change the constraint's own status.
                        return INIT;
                    }
                }

                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    // This state was reached because 'B' happened first. We are in the timer window.
                    if (actEvents.contains(event)) {
                        // An 'ACTIVATION' (A) happens *after* 'B', stopping the timer.
                        // The constraint is no longer in violation.
                        return INIT;
                    }
                }
                break;

            case CHAIN_PRECEDENCE:
                if (INIT.equals(currentStatus)) {
                    if (trgEvents.contains(event)) return PERMANENT_VIOLATION;
                    if (actEvents.contains(event))
                        return TEMPORARY_VIOLATION; // Waiting for immediate target
                }
                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    // If target, good. If anything else, chain is broken, reset.
                    return trgEvents.contains(event) ? FULFILLED : INIT;
                }
                break;

            // --- Negative Relations ---
            case NOT_RESPONSE:
                if (INIT.equals(currentStatus)) {
                    if (actEvents.contains(event)) {
                        return TEMPORARY_VIOLATION;
                    }
                    if (trgEvents.contains(event)) {
                        if (traceContainsAny(trace, actEvents)) {
                            return PERMANENT_VIOLATION;
                        }
                        return INIT;
                    }
                }

                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (trgEvents.contains(event)) {
                        return PERMANENT_VIOLATION;
                    }
                }
                break;

            case NOT_PRECEDENCE:
                if (INIT.equals(currentStatus)) {
                    if (trgEvents.contains(event)) {
                        // --- TRACE-AWARE LOGIC ---
                        // This is Event 'B' (target). We must check the trace for 'A' (activation).
                        boolean activationEventExists = traceContainsAny(trace, actEvents);

                        if (activationEventExists) {
                            // 'A' IS in the trace. 'B' happened *after* 'A'.
                            // This is the violation.
                            return PERMANENT_VIOLATION;
                        } else {
                            // 'A' IS NOT in the trace. 'B' happened *without* 'A' before it.
                            // This is the "good" path.
                            return FULFILLED;
                        }
                    }
                    if (actEvents.contains(event)) {
                        // This is Event 'A'. It doesn't cause a violation yet,
                        // but it "arms" the constraint. We use TEMPORARY_VIOLATION
                        // to represent this "armed" state.
                        return TEMPORARY_VIOLATION;
                    }
                }

                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    // This status means 'A' has already happened (the rule is "armed").
                    if (trgEvents.contains(event)) {
                        // Event 'B' happens *after* 'A'. This is the violation.
                        return PERMANENT_VIOLATION;
                    }
                    if (actEvents.contains(event)) {
                        // Another 'A' event just keeps the rule "armed".
                        return TEMPORARY_VIOLATION;
                    }
                }
                break;
        }

        // If no state change was triggered
        return currentStatus;
    }
}