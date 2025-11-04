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
                    reasons.add(String.format("Failed: %s (%s) is permanently violated.", constraint.getName(), constraint.getType()));
                    break;

                case TEMPORARY_VIOLATION:
                    // Condition 2: Cannot finish if an obligation is actively pending.
                    // (e.g., RESPONSE waiting for target, PRECEDENCE in timer window)
                    reasons.add(String.format("Pending: %s (%s) is in a temporary violation state.", constraint.getName(), constraint.getType()));
                    break;

                case FULFILLED:
                default:
                    // FULFILLED is good.
                    // Other states are considered "finishable".
                    break;
            }
        }

        return Map.of("canFinish", reasons.isEmpty(), "reasons", reasons);
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
                    log.debug("  [!] Task '{}' is UNSAFE. Violates: {} ({})", event, constraint.getName(), constraint.getType());
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
        return Arrays.stream(eventField.split(",")).map(String::trim).collect(Collectors.toList());
    }

    private boolean traceContainsAny(List<String> trace, List<String> eventsToFind) {
        if (eventsToFind == null || eventsToFind.isEmpty() || trace == null || trace.isEmpty()) {
            return false;
        }
        Set<String> eventsToFindSet = new HashSet<>(eventsToFind);
        return !Collections.disjoint(trace, eventsToFindSet);
    }

    private ConstraintStatus getHypotheticalStatus(Constraint constraint, String event, List<String> trace) {
        ConstraintType cType = constraint.getType();
        ConstraintStatus currentStatus = constraint.getStatus() != null ? constraint.getStatus() : INIT;

        List<String> actEvents = getEventsFromField(constraint.getActivationEvent());
        List<String> trgEvents = getEventsFromField(constraint.getTargetEvent());

        boolean isDirectlyRelevant = actEvents.contains(event) || trgEvents.contains(event);

        if (!isDirectlyRelevant) {
            // Event is not Activation or Target.
            if (cType == CHAIN_RESPONSE && TEMPORARY_VIOLATION.equals(currentStatus)) {
                // FIXME check for chain
            } else {
                // For all other constraints, this event is irrelevant.
                return currentStatus;
            }
        }

        switch (cType) {
            case EXISTENCE:
                if (actEvents.contains(event)) return FULFILLED;
                break;

            case NOT_EXISTENCE:
                if (trgEvents.contains(event)) return PERMANENT_VIOLATION;
                break;

            case RESPONSE:
            case ALTERNATE_RESPONSE:
                if (INIT.equals(currentStatus) && actEvents.contains(event)) return TEMPORARY_VIOLATION;
                if (TEMPORARY_VIOLATION.equals(currentStatus) && trgEvents.contains(event)) return FULFILLED;
                break;

            case RESPONDED_EXISTENCE:
                if (INIT.equals(currentStatus)) {
                    if (actEvents.contains(event)) {
                        if (traceContainsAny(trace, trgEvents)) {
                            return FULFILLED;
                        } else {
                            return TEMPORARY_VIOLATION;
                        }
                    }
                }

                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (trgEvents.contains(event)) {
                        return FULFILLED;
                    }
                    if (actEvents.contains(event)) {
                        return TEMPORARY_VIOLATION;
                    }
                }
                break;

            case CHAIN_RESPONSE:
                if (INIT.equals(currentStatus)) {
                    if (actEvents.contains(event)) return TEMPORARY_VIOLATION;

                } else if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (trgEvents.contains(event)) {
                        return FULFILLED;
                    }
                    if (actEvents.contains(event)) {
                        return TEMPORARY_VIOLATION;
                    }

                    return PERMANENT_VIOLATION;
                }
                break;

            case PRECEDENCE:
            case ALTERNATE_PRECEDENCE:
                if (INIT.equals(currentStatus)) {
                    if (trgEvents.contains(event)) {
                        boolean activationEventExists = traceContainsAny(trace, actEvents);

                        if (activationEventExists) {
                            return FULFILLED;
                        } else {
                            return PERMANENT_VIOLATION;
                        }
                    }
                    if (actEvents.contains(event)) {
                        return INIT;
                    }
                }

                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (actEvents.contains(event)) {
                        return INIT;
                    }
                }
                break;

            case CHAIN_PRECEDENCE:
                if (INIT.equals(currentStatus)) {
                    if (trgEvents.contains(event)) return PERMANENT_VIOLATION;
                    if (actEvents.contains(event)) return TEMPORARY_VIOLATION;
                }
                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    return trgEvents.contains(event) ? FULFILLED : INIT;
                }
                break;

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
                        boolean activationEventExists = traceContainsAny(trace, actEvents);

                        if (activationEventExists) {
                            return PERMANENT_VIOLATION;
                        } else {
                            return FULFILLED;
                        }
                    }
                    if (actEvents.contains(event)) {
                        return TEMPORARY_VIOLATION;
                    }
                }

                if (TEMPORARY_VIOLATION.equals(currentStatus)) {
                    if (trgEvents.contains(event)) {
                        return PERMANENT_VIOLATION;
                    }
                    if (actEvents.contains(event)) {
                        return TEMPORARY_VIOLATION;
                    }
                }
                break;
        }

        return currentStatus;
    }
}