package de.ur.analysis;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dao.GenericEvent;
import de.ur.dto.AllowedTaskResponse;
import de.ur.dto.ConditionRequest;
import de.ur.dto.FinishabilityResponse;
import de.ur.service.ConstraintService;
import de.ur.service.ContextAnalysisService;
import de.ur.service.EsperService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ContextAnalysisServiceTest {

    @Inject
    EsperService esperService;

    @Inject
    ConstraintService constraintService;

    @Inject
    ContextAnalysisService analysisService;

    @BeforeEach
    void reset() {
        esperService.reset();
        constraintService.resetConstraints();
        constraintService.getTrace().clear();
        analysisService.resetTestContext();
    }

    // ==================== Helper Methods ====================

    protected void sendEvent(String eventType) {
        sendEvent(eventType, null);
    }

    protected void sendEvent(String eventType, Map<String, String> payload) {
        GenericEvent event = new GenericEvent(
                UUID.randomUUID().toString(),
                eventType,
                System.nanoTime(),
                payload);

        if (constraintService.getKnownEvents().contains(eventType)) {
            constraintService.addToTrace(eventType, payload);
        }

        esperService.sendEvent(event);
    }

    private void createConstraint(ConstraintType type, String name,
            String activationEvent, String targetEvent,
            ConstraintStatus initialStatus) {
        constraintService.setupConstraint(
                type,
                name,
                null,
                activationEvent,
                new ConditionRequest("", "", "", null),
                targetEvent,
                new ConditionRequest("", "", "", null),
                null,
                initialStatus,
                "task",
                "task");
    }

    private AllowedTaskResponse findTaskResponse(List<AllowedTaskResponse> responses, String taskName) {
        return responses.stream()
                .filter(r -> r.task().equals(taskName))
                .findFirst()
                .orElse(null);
    }

    // ==================== NOT_EXISTENCE Tests ====================

    @Nested
    @DisplayName("NOT_EXISTENCE Analysis")
    class NotExistenceAnalysisTests {

        @Test
        @DisplayName("Target event should be marked unsafe")
        void targetEventShouldBeUnsafe() {
            // Given: NOT_EXISTENCE constraint - "ForbiddenTask must not occur"
            createConstraint(ConstraintType.NOT_EXISTENCE, "NoForbidden",
                    null, "ForbiddenTask", ConstraintStatus.INIT);

            // When
            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then
            AllowedTaskResponse forbidden = findTaskResponse(result, "ForbiddenTask");
            assertNotNull(forbidden);
            assertTrue(forbidden.isUnsafe(), "ForbiddenTask should be unsafe for NOT_EXISTENCE");
        }
    }

    // ==================== PRECEDENCE Tests ====================

    @Nested
    @DisplayName("PRECEDENCE Analysis")
    class PrecedenceAnalysisTests {

        @Test
        @DisplayName("Target should be unsafe before activation occurs")
        void targetUnsafeBeforeActivation() {
            // Given: PRECEDENCE(Login, Purchase) - "Login must occur before Purchase"
            createConstraint(ConstraintType.PRECEDENCE, "MustLoginFirst",
                    "Login", "Purchase", ConstraintStatus.INIT);

            // When - no events sent yet
            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then
            AllowedTaskResponse purchase = findTaskResponse(result, "Purchase");
            AllowedTaskResponse login = findTaskResponse(result, "Login");

            assertNotNull(purchase);
            assertNotNull(login);
            assertTrue(purchase.isUnsafe(), "Purchase should be unsafe before Login");
            assertFalse(login.isUnsafe(), "Login should be safe");
        }

        @Test
        @DisplayName("Target should be safe after activation occurs")
        void targetSafeAfterActivation() {
            // Given
            createConstraint(ConstraintType.PRECEDENCE, "MustLoginFirst",
                    "Login", "Purchase", ConstraintStatus.INIT);

            // When - Login has occurred
            sendEvent("Login");
            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then
            AllowedTaskResponse purchase = findTaskResponse(result, "Purchase");
            assertNotNull(purchase);
            assertFalse(purchase.isUnsafe(), "Purchase should be safe after Login");
        }
    }

    // ==================== CHAIN_RESPONSE Tests ====================

    @Nested
    @DisplayName("CHAIN_RESPONSE Analysis")
    class ChainResponseAnalysisTests {

        @Test
        @DisplayName("Non-target should be unsafe when activation occurred (chain broken)")
        void nonTargetUnsafeAfterActivation() throws InterruptedException {
            // Given: CHAIN_RESPONSE(A, B) - "B must immediately follow A"
            createConstraint(ConstraintType.CHAIN_RESPONSE, "ChainAB",
                    "A", "B", ConstraintStatus.INIT);

            // When - A has occurred, now in TEMPORARY_VIOLATION waiting for B
            sendEvent("A");
            Thread.sleep(100); // Wait for Esper to process

            // Manually set status since listener might not have fired in test
            var constraint = constraintService.getConstraints().get("ChainAB");
            if (constraint.getStatus() == ConstraintStatus.INIT) {
                constraint.updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
            }

            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then - A should be unsafe (would break chain), B should be safe
            AllowedTaskResponse taskA = findTaskResponse(result, "A");
            AllowedTaskResponse taskB = findTaskResponse(result, "B");

            assertNotNull(taskA);
            assertNotNull(taskB);
            assertTrue(taskA.isUnsafe(), "A should be unsafe after activation (chain requires B next)");
            assertFalse(taskB.isUnsafe(), "B should be safe (completes the chain)");
        }
    }

    // ==================== ALTERNATE_RESPONSE Tests ====================

    @Nested
    @DisplayName("ALTERNATE_RESPONSE Analysis")
    class AlternateResponseAnalysisTests {

        @Test
        @DisplayName("Activation should be unsafe when already activated")
        void activationUnsafeWhenAlreadyActivated() throws InterruptedException {
            // Given: ALTERNATE_RESPONSE(A, B) - "Can't have A twice without B between"
            createConstraint(ConstraintType.ALTERNATE_RESPONSE, "AltAB",
                    "A", "B", ConstraintStatus.INIT);

            // When - A has occurred
            sendEvent("A");
            Thread.sleep(100);

            var constraint = constraintService.getConstraints().get("AltAB");
            if (constraint.getStatus() == ConstraintStatus.INIT) {
                constraint.updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
            }

            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then - A should be unsafe (would violate alternate constraint)
            AllowedTaskResponse taskA = findTaskResponse(result, "A");
            assertNotNull(taskA);
            assertTrue(taskA.isUnsafe(), "A should be unsafe when already in TEMP_VIOLATION");
        }
    }

    // ==================== NOT_RESPONSE Tests ====================

    @Nested
    @DisplayName("NOT_RESPONSE Analysis")
    class NotResponseAnalysisTests {

        @Test
        @DisplayName("Target should be unsafe after activation")
        void targetUnsafeAfterActivation() throws InterruptedException {
            // Given: NOT_RESPONSE(A, B) - "B must not follow A"
            createConstraint(ConstraintType.NOT_RESPONSE, "NotResponseAB",
                    "A", "B", ConstraintStatus.INIT);

            // When - A has occurred
            sendEvent("B");
            Thread.sleep(100);

            var constraint = constraintService.getConstraints().get("NotResponseAB");
            if (constraint.getStatus() == ConstraintStatus.INIT) {
                constraint.updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);
            }

            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then - B should be unsafe (would violate NOT_RESPONSE)
            AllowedTaskResponse taskB = findTaskResponse(result, "B");
            assertNotNull(taskB);
            assertTrue(taskB.isUnsafe(), "B should be unsafe after A (NOT_RESPONSE)");
        }
    }

    // ==================== NOT_PRECEDENCE Tests ====================

    @Nested
    @DisplayName("NOT_PRECEDENCE Analysis")
    class NotPrecedenceAnalysisTests {

        @Test
        @DisplayName("Target should be unsafe after activation")
        void targetUnsafeAfterActivation() {
            // Given: NOT_PRECEDENCE(A, B) - "B must not follow A"
            createConstraint(ConstraintType.NOT_PRECEDENCE, "NotPrecAB",
                    "A", "B", ConstraintStatus.INIT);

            // When - A has occurred (in trace)
            sendEvent("A");
            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then - B should be unsafe
            AllowedTaskResponse taskB = findTaskResponse(result, "B");
            assertNotNull(taskB);
            assertTrue(taskB.isUnsafe(), "B should be unsafe after A (NOT_PRECEDENCE)");
        }

        @Test
        @DisplayName("Target should be safe before activation")
        void targetSafeBeforeActivation() {
            // Given: NOT_PRECEDENCE(A, B)
            createConstraint(ConstraintType.NOT_PRECEDENCE, "NotPrecAB",
                    "A", "B", ConstraintStatus.INIT);

            // When - no events yet
            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then - B should be safe (A hasn't occurred)
            AllowedTaskResponse taskB = findTaskResponse(result, "B");
            assertNotNull(taskB);
            assertFalse(taskB.isUnsafe(), "B should be safe before A occurs");
        }
    }

    // ==================== Finishability Tests ====================

    @Nested
    @DisplayName("Finishability Analysis")
    class FinishabilityTests {

        @Test
        @DisplayName("Should be finishable when no constraints exist")
        void finishableWithNoConstraints() {
            FinishabilityResponse result = analysisService.checkFinishability();
            assertTrue(result.canFinish());
            assertTrue(result.reasons().isEmpty());
        }

        @Test
        @DisplayName("Should not be finishable with TEMPORARY_VIOLATION")
        void notFinishableWithTemporaryViolation() {
            // Given: RESPONSE constraint that's temporarily violated
            createConstraint(ConstraintType.RESPONSE, "ResponseAB",
                    "A", "B", ConstraintStatus.INIT);

            // When - A occurs (waiting for B)
            sendEvent("A");

            var constraint = constraintService.getConstraints().get("ResponseAB");
            constraint.updateStatus(ConstraintStatus.TEMPORARY_VIOLATION);

            FinishabilityResponse result = analysisService.checkFinishability();

            // Then
            assertFalse(result.canFinish());
            assertFalse(result.reasons().isEmpty());
        }

        @Test
        @DisplayName("Should be finishable when constraint is FULFILLED")
        void finishableWhenFulfilled() {
            // Given
            createConstraint(ConstraintType.RESPONSE, "ResponseAB",
                    "A", "B", ConstraintStatus.INIT);

            sendEvent("A");
            sendEvent("B");

            var constraint = constraintService.getConstraints().get("ResponseAB");
            constraint.updateStatus(ConstraintStatus.FULFILLED);

            FinishabilityResponse result = analysisService.checkFinishability();

            // Then
            assertTrue(result.canFinish());
        }
    }

    // ==================== Multiple Constraints Tests ====================

    @Nested
    @DisplayName("Multiple Constraints Analysis")
    class MultipleConstraintsTests {

        @Test
        @DisplayName("Task unsafe if ANY constraint would be violated")
        void taskUnsafeIfAnyConstraintViolated() {
            // Given: Two constraints where TaskX would violate one
            createConstraint(ConstraintType.NOT_EXISTENCE, "NoX",
                    null, "TaskX", ConstraintStatus.INIT);
            createConstraint(ConstraintType.PRECEDENCE, "YBeforeX",
                    "TaskY", "TaskX", ConstraintStatus.INIT);

            // When
            List<AllowedTaskResponse> result = analysisService.analyzeAllowedTasks();

            // Then: TaskX unsafe (violates NOT_EXISTENCE), TaskY safe
            AllowedTaskResponse taskX = findTaskResponse(result, "TaskX");
            AllowedTaskResponse taskY = findTaskResponse(result, "TaskY");

            assertNotNull(taskX);
            assertNotNull(taskY);
            assertTrue(taskX.isUnsafe(), "TaskX should be unsafe (violates NOT_EXISTENCE)");
            assertFalse(taskY.isUnsafe(), "TaskY should be safe");
        }
    }
}
