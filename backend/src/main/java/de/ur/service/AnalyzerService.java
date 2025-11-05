package de.ur.service;

import de.ur.dao.Constraint;
import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static de.ur.dao.ConstraintStatus.*;
import static de.ur.dao.ConstraintType.*;

@ApplicationScoped
@Slf4j
public class AnalyzerService {
    public Map<String, Object> checkFinishability(@NonNull List<Constraint> constraints) {
        List<String> reasons = constraints.stream()
                .filter(constraint -> {
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

    public Map<String, Boolean> analyzeAllowedTasks(@NonNull List<Constraint> constraints,
                                                    @NonNull Set<String> possibleEvents,
                                                    @NonNull List<String> trace) {
        Map<String, Boolean> analysis = new HashMap<>();

        for (String event : possibleEvents) {
            boolean isUnsafe = constraints.stream()
                    .filter(constraint -> {
                        ConstraintStatus currentStatus = getStatusOrInit(constraint);
                        return !FULFILLED.equals(currentStatus) && !PERMANENT_VIOLATION.equals(currentStatus);
                    })
                    .anyMatch(constraint -> {
                        ConstraintStatus hypothetical = getHypotheticalStatus(constraint, event, trace);
                        boolean violates = PERMANENT_VIOLATION.equals(hypothetical);
                        if (violates) {
                            log.debug("  [!] Event '{}' unsafe: violates {} ({})", event, constraint.getName(), constraint.getType());
                        }
                        return violates;
                    });

            analysis.put(event, isUnsafe);
            if (!isUnsafe) {
                log.debug("  [✓] Event '{}' is safe.", event);
            }
        }
        return analysis;
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

    private boolean traceContainsAny(@NonNull List<String> trace, List<String> eventsToFind) {
        if (eventsToFind == null || eventsToFind.isEmpty() || trace.isEmpty()) {
            return false;
        }
        Set<String> eventsSet = new HashSet<>(eventsToFind);
        return trace.stream().anyMatch(eventsSet::contains);
    }

    private boolean isLastEventInTrace(@NonNull List<String> trace, List<String> events) {
        if (trace.isEmpty() || events == null || events.isEmpty()) {
            return false;
        }
        return events.contains(trace.getLast());
    }

    private ConstraintStatus getHypotheticalStatus(Constraint constraint, String event, List<String> trace) {
        ConstraintType cType = constraint.getType();
        ConstraintStatus currentStatus = getStatusOrInit(constraint);

        List<String> actEvents = getEventsFromField(constraint.getActivationEvent());
        List<String> trgEvents = getEventsFromField(constraint.getTargetEvent());

        boolean isAct = actEvents.contains(event);
        boolean isTrg = trgEvents.contains(event);
        boolean isDirectlyRelevant = isAct || isTrg;

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
                boolean seenAct = traceContainsAny(trace, actEvents) || isAct;
                boolean seenTrg = traceContainsAny(trace, trgEvents) || isTrg;
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
                if (INIT.equals(currentStatus)) {
                    if (isTrg) {
                        boolean priorAct = traceContainsAny(trace, actEvents);
                        // For basic precedence: FUL if prior A, else PERM
                        // Note: ALTERNATE_PRECEDENCE requires additional state for "no intervening B"; approximated here
                        yield priorAct ? FULFILLED : PERMANENT_VIOLATION;
                    }
                    // Act event does not change state for precedence
                    yield currentStatus;
                }
                if (TEMPORARY_VIOLATION.equals(currentStatus) && isAct) {
                    yield INIT;
                }
                yield currentStatus;
            }

            case CHAIN_PRECEDENCE -> {
                if (isTrg) {
                    // Chain: B must be immediately after A
                    boolean immediatePriorAct = !trace.isEmpty() && isLastEventInTrace(trace, actEvents);
                    yield immediatePriorAct ? FULFILLED : PERMANENT_VIOLATION;
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
                        boolean priorAct = traceContainsAny(trace, actEvents);
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
                        boolean priorAct = traceContainsAny(trace, actEvents);
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
