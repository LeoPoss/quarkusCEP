package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ALTERNATE_RESPONSE constraint type.
 * ALTERNATE_RESPONSE(A, B) = "Each A must be followed by B before another A"
 */
@QuarkusTest
class AlternateResponseConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("ALTERNATE_RESPONSE: Should start in INIT state")
    void shouldStartInInitState() {
        // Given
        createAlternateResponseConstraint("alt_resp_AB", "A", "B");

        // Then
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("alt_resp_AB"),
                "ALTERNATE_RESPONSE should start in INIT state");
    }

    @Test
    @DisplayName("ALTERNATE_RESPONSE: Should become TEMPORARY_VIOLATION after activation")
    void shouldBeTemporaryViolationAfterActivation() {
        // Given
        createAlternateResponseConstraint("alt_resp_AB", "A", "B");

        // When
        sendEvent("A");

        // Wait for timer - using Awaitility
        org.awaitility.Awaitility.await()
                .atMost(2, java.util.concurrent.TimeUnit.SECONDS)
                .pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("alt_resp_AB"),
                            "ALTERNATE_RESPONSE should be TEMPORARY_VIOLATION after activation");
                });
    }

    @Test
    @DisplayName("ALTERNATE_RESPONSE: Should be FULFILLED when B follows A")
    void shouldFulfillWhenTargetFollowsActivation() {
        // Given
        createAlternateResponseConstraint("alt_resp_AB", "A", "B");

        // When
        sendEvent("A");
        sendEvent("B");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("alt_resp_AB"),
                "ALTERNATE_RESPONSE should be FULFILLED when B follows A");
    }

    @Test
    @DisplayName("ALTERNATE_RESPONSE: Should VIOLATE when A occurs twice without B")
    void shouldViolateWhenActivationRepeatswithoutTarget() {
        // Given
        createAlternateResponseConstraint("alt_resp_AB", "A", "B");

        // When - A twice without B
        sendEvent("A");
        sendEvent("A"); // second A without B in between

        // Then
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("alt_resp_AB"),
                "ALTERNATE_RESPONSE should be PERMANENT_VIOLATION when A repeats without B");
    }

    @Test
    @DisplayName("ALTERNATE_RESPONSE: Should allow A-B-A-B sequence")
    void shouldAllowAlternatingSequence() {
        // Given
        createAlternateResponseConstraint("alt_resp_AB", "A", "B");

        // When - proper alternation
        sendEvent("A");
        sendEvent("B");
        sendEvent("A");
        sendEvent("B");

        // Then - should be fulfilled
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("alt_resp_AB"),
                "ALTERNATE_RESPONSE should be FULFILLED with proper alternation");
    }

    // Helper method
    private void createAlternateResponseConstraint(String name, String activationEvent, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.ALTERNATE_RESPONSE,
                name,
                null,
                activationEvent,
                new ConditionRequest("", "", "", null),
                targetEvent,
                new ConditionRequest("", "", "", null),
                null,
                ConstraintStatus.INIT,
                "task",
                "task");
    }
}
