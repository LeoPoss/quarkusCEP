package de.ur.service;

import de.ur.dao.*;
import de.ur.dto.AllowedTaskResponse;
import de.ur.dto.FinishabilityResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static de.ur.dao.ConstraintStatus.*;
import static de.ur.dao.ConstraintType.*;

/**
 * Service that analyzes constraint states using Esper's TestSessionContext.
 * 
 * For each hypothetical event test:
 * 1. Sends TestStartEvent with constraint state info -> initiates context
 * partition
 * 2. Analysis patterns in context check if violation would occur
 * 3. Sends TestEndEvent -> terminates context partition
 * 
 * This delegates all violation logic to Esper EPL patterns.
 */
@ApplicationScoped
@Slf4j
public class ContextAnalysisService {

    @Inject
    ConstraintService constraintService;

    @Inject
    EsperService esperService;

    // Track violations detected by Esper patterns
    private final Map<String, Set<String>> testViolations = new ConcurrentHashMap<>();

    private boolean testContextDeployed = false;
    private int testCounter = 0;

    /**
     * Check if the process can be finished based on current constraint states.
     */
    public FinishabilityResponse checkFinishability() {
        List<Constraint> constraints = constraintService.getConstraints().values().stream().toList();

        List<String> reasons = constraints.stream()
                .filter(constraint -> {
                    ConstraintStatus status = getStatus(constraint);

                    if (NOT_EXISTENCE.equals(constraint.getType()) || NOT_RESPONSE.equals(constraint.getType())) {
                        return PERMANENT_VIOLATION.equals(status);
                    }
                    return PERMANENT_VIOLATION.equals(status) || TEMPORARY_VIOLATION.equals(status);
                })
                .map(constraint -> {
                    ConstraintStatus status = getStatus(constraint);
                    String state = PERMANENT_VIOLATION.equals(status) ? "permanently" : "temporarily";
                    return String.format("%s violated: %s (%s)", state, constraint.getName(), constraint.getType());
                })
                .toList();

        return new FinishabilityResponse(reasons.isEmpty(), reasons);
    }

    /**
     * Get the current process trace.
     */
    public List<Map<String, Object>> getTrace() {
        return constraintService.getTrace();
    }

    /**
     * Analyze which tasks are safe to execute using Esper's TestSessionContext.
     */
    public List<AllowedTaskResponse> analyzeAllowedTasks() {
        Set<String> possibleEvents = constraintService.getKnownEvents();
        Collection<Constraint> constraints = constraintService.getConstraints().values();
        List<Map<String, Object>> trace = constraintService.getTrace();

        if (possibleEvents.isEmpty()) {
            return Collections.emptyList();
        }

        // Ensure test context and analysis patterns are deployed
        ensureTestContextDeployed();

        return possibleEvents.stream().map(event -> {
            // Test this event against all constraints using Esper
            String testId = "test_" + (testCounter++);
            Set<String> violations = testEventInEsper(testId, event, constraints, trace);

            if (!violations.isEmpty()) {
                Map<String, String> conditions = new HashMap<>();
                conditions.put("violates", String.join(", ", violations));
                log.debug("  [!] Event '{}' is unsafe: would violate {}", event, violations);
                return AllowedTaskResponse.unsafe(event, conditions);
            }

            log.debug("  [✓] Event '{}' is safe.", event);
            return AllowedTaskResponse.safe(event);
        }).collect(Collectors.toList());
    }

    /**
     * Ensure the test context and analysis patterns are deployed.
     */
    private synchronized void ensureTestContextDeployed() {
        if (testContextDeployed) {
            return;
        }

        // Deploy the test session context
        esperService.deployTestContext();

        // Deploy analysis patterns for each constraint with violation callback
        for (Constraint constraint : constraintService.getConstraints().values()) {
            String activationEvent = constraint.getActivationEvent() != null
                    ? constraint.getActivationEvent().name()
                    : null;
            String targetEvent = constraint.getTargetEvent() != null
                    ? constraint.getTargetEvent().name()
                    : null;

            esperService.deployAnalysisPattern(
                    constraint.getName(),
                    constraint.getType(),
                    activationEvent,
                    targetEvent,
                    // Violation callback - adds constraint to violations set for that testId
                    (testId, constraintName) -> {
                        Set<String> violations = testViolations.get(testId);
                        if (violations != null) {
                            violations.add(constraintName);
                        }
                    });
        }

        testContextDeployed = true;
        log.info("Test context and analysis patterns deployed");
    }

    /**
     * Test a hypothetical event in Esper by sending TestStartEvent for each
     * constraint.
     */
    private Set<String> testEventInEsper(String testId, String hypotheticalEvent,
            Collection<Constraint> constraints,
            List<Map<String, Object>> trace) {
        Set<String> violations = ConcurrentHashMap.newKeySet();

        // Prepare trace info
        boolean hasActivationInTrace = false;
        String lastEventType = getLastEventType(trace);

        for (Constraint constraint : constraints) {
            ConstraintStatus currentStatus = getStatus(constraint);

            // Skip terminal states
            if (FULFILLED.equals(currentStatus) || PERMANENT_VIOLATION.equals(currentStatus)) {
                continue;
            }

            // Check if activation exists in trace for this constraint
            String activationEvent = constraint.getActivationEvent() != null
                    ? constraint.getActivationEvent().name()
                    : null;
            hasActivationInTrace = activationEvent != null && hasEventInTrace(trace, activationEvent);

            // Create unique test ID for this constraint test
            String constraintTestId = testId + "_" + constraint.getName();

            // Register this test ID so the callback can find the violations set
            testViolations.put(constraintTestId, violations);

            // Send TestStartEvent to initiate context partition
            TestStartEvent startEvent = new TestStartEvent(
                    constraintTestId,
                    constraint.getName(),
                    currentStatus != null ? currentStatus.name() : "INIT",
                    hypotheticalEvent,
                    hasActivationInTrace,
                    lastEventType);
            esperService.sendEvent(startEvent);

            // Wait briefly for pattern to fire (if it will)
            try {
                // Short pause to ensure Esper processes the event and listeners fire
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Send TestEndEvent to terminate context partition
            esperService.sendEvent(new TestEndEvent(constraintTestId));

            // Clean up
            testViolations.remove(constraintTestId);
        }

        return violations;
    }

    // ==================== Helper Methods ====================

    private ConstraintStatus getStatus(Constraint constraint) {
        return constraint.getStatus() != null ? constraint.getStatus() : INIT;
    }

    private boolean hasEventInTrace(List<Map<String, Object>> trace, String eventName) {
        if (eventName == null || trace == null)
            return false;
        return trace.stream().anyMatch(e -> eventName.equals(e.get("eventType")));
    }

    private String getLastEventType(List<Map<String, Object>> trace) {
        if (trace == null || trace.isEmpty())
            return null;
        return (String) trace.get(trace.size() - 1).get("eventType");
    }

    /**
     * Reset test context state (call when Esper is reset).
     */
    public void resetTestContext() {
        testContextDeployed = false;
        testViolations.clear();
    }
}
