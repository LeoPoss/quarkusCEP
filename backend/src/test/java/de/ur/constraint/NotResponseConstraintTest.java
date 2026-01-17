package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NOT_RESPONSE constraint type.
 * NOT_RESPONSE(A, B) = "If A occurs, B must not occur afterwards"
 */
@QuarkusTest
class NotResponseConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("NOT_RESPONSE: Should start in INIT state")
    void shouldStartInInitState() {
        // Given
        createNotResponseConstraint("not_resp_AB", "A", "B");

        // Then
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("not_resp_AB"),
                "NOT_RESPONSE should start in INIT state");
    }

    @Test
    @DisplayName("NOT_RESPONSE: Should become TEMPORARY_VIOLATION after activation")
    void shouldBeTemporaryViolationAfterActivation() {
        // Given
        createNotResponseConstraint("not_resp_AB", "A", "B");

        // When
        sendEvent("A");

        // Then - now watching for B (which must not occur)
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("not_resp_AB"),
                "NOT_RESPONSE should be TEMPORARY_VIOLATION after activation");
    }

    @Test
    @DisplayName("NOT_RESPONSE: Should become PERMANENT_VIOLATION when B follows A")
    void shouldViolateWhenTargetFollowsActivation() {
        // Given
        createNotResponseConstraint("not_resp_AB", "A", "B");

        // When
        sendEvent("A");
        sendEvent("B"); // violates!

        // Then
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("not_resp_AB"),
                "NOT_RESPONSE should be PERMANENT_VIOLATION when B follows A");
    }

    @Test
    @DisplayName("NOT_RESPONSE: B before A should not violate")
    void targetBeforeActivationShouldNotViolate() {
        // Given
        createNotResponseConstraint("not_resp_AB", "A", "B");

        // When - B before A
        sendEvent("B");
        sendEvent("A");

        // Then - still just TEMP_VIOLATION (A happened, but B was before)
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("not_resp_AB"),
                "NOT_RESPONSE should not violate when B occurred before A");
    }

    @Test
    @DisplayName("NOT_RESPONSE: Unrelated events after A should not affect")
    void unrelatedEventsAfterActivationShouldNotAffect() {
        // Given
        createNotResponseConstraint("not_resp_AB", "A", "B");

        // When
        sendEvent("A");
        sendEvent("C");
        sendEvent("D");

        // Then - still TEMP_VIOLATION
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("not_resp_AB"),
                "NOT_RESPONSE should remain TEMPORARY_VIOLATION with unrelated events");
    }

    // Helper method
    private void createNotResponseConstraint(String name, String activationEvent, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.NOT_RESPONSE,
                name,
                5L, // timer required for NOT_RESPONSE queries
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
