package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RESPONSE constraint type.
 * RESPONSE(A, B) = "If A occurs, then B must occur afterwards"
 */
@QuarkusTest
class ResponseConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("RESPONSE: Should start in INIT state")
    void shouldStartInInitState() {
        // Given
        createResponseConstraint("response_AB", "A", "B");

        // Then
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("response_AB"),
                "RESPONSE constraint should start in INIT state");
    }

    @Test
    @DisplayName("RESPONSE: Should become TEMPORARY_VIOLATION when activation occurs without target")
    void shouldBeTemporaryViolationAfterActivation() {
        // Given
        createResponseConstraint("response_AB", "A", "B");

        // When - send activation event only
        sendEvent("A");

        // Then
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("response_AB"),
                "RESPONSE should be TEMPORARY_VIOLATION after activation without target");
    }

    @Test
    @DisplayName("RESPONSE: Should become FULFILLED when target follows activation")
    void shouldFulfillWhenTargetFollowsActivation() {
        // Given
        createResponseConstraint("response_AB", "A", "B");

        // When - send A then B
        sendEvent("A");
        sendEvent("B");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("response_AB"),
                "RESPONSE should be FULFILLED when target follows activation");
    }

    @Test
    @DisplayName("RESPONSE: Target occurring before activation should not affect constraint")
    void targetBeforeActivationShouldNotFulfill() {
        // Given
        createResponseConstraint("response_AB", "A", "B");

        // When - send B first (no activation yet)
        sendEvent("B");

        // Then - still INIT because A hasn't occurred
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("response_AB"),
                "RESPONSE should remain INIT when target occurs before activation");

        // When - now send A
        sendEvent("A");

        // Then - should be TEMP_VIOLATION because we need B after A
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("response_AB"),
                "RESPONSE should be TEMPORARY_VIOLATION after A (needs B)");

        // When - send B again
        sendEvent("B");

        // Then - now fulfilled
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("response_AB"),
                "RESPONSE should be FULFILLED after A->B sequence");
    }

    @Test
    @DisplayName("RESPONSE: Multiple RESPONSE constraints should be independent")
    void multipleResponseConstraintsShouldBeIndependent() {
        // Given
        createResponseConstraint("response_AB", "A", "B");
        createResponseConstraint("response_CD", "C", "D");

        // When - activate only first constraint
        sendEvent("A");

        // Then
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("response_AB"),
                "response_AB should be TEMPORARY_VIOLATION");
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("response_CD"),
                "response_CD should remain INIT");
    }

    @Test
    @DisplayName("RESPONSE: Unrelated events should not affect constraint")
    void unrelatedEventsShouldNotAffect() {
        // Given
        createResponseConstraint("response_AB", "A", "B");
        sendEvent("A"); // activate
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("response_AB"));

        // When - send unrelated events
        sendEvent("X");
        sendEvent("Y");
        sendEvent("Z");

        // Then - still temporary violation
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("response_AB"),
                "RESPONSE should not change from unrelated events");

        // When - send target
        sendEvent("B");

        // Then - now fulfilled
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("response_AB"),
                "RESPONSE should be FULFILLED after target");
    }

    // Helper method to create RESPONSE constraint
    private void createResponseConstraint(String name, String activationEvent, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.RESPONSE,
                name,
                null, // timer
                activationEvent, // activationEventName
                new ConditionRequest("", "", "", null), // activationCondition
                targetEvent, // targetEventName
                new ConditionRequest("", "", "", null), // targetCondition
                null, // correlationCondition
                ConstraintStatus.INIT, // initial status
                "task", // activationEventType
                "task" // targetEventType
        );
    }
}
