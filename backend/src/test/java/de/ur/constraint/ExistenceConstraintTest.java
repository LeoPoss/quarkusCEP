package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EXISTENCE constraint type.
 * EXISTENCE(A) = "A must occur at some point"
 */
@QuarkusTest
class ExistenceConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("EXISTENCE: Should start in TEMPORARY_VIOLATION state")
    void shouldStartInTemporaryViolation() {
        // Given
        createExistenceConstraint("exist_A", "A");

        // Then
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("exist_A"),
                "EXISTENCE constraint should start in TEMPORARY_VIOLATION");
    }

    @Test
    @DisplayName("EXISTENCE: Should become FULFILLED when target event occurs")
    void shouldFulfillWhenTargetEventOccurs() {
        // Given
        createExistenceConstraint("exist_A", "A");

        // When
        sendEvent("A");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("exist_A"),
                "EXISTENCE constraint should be FULFILLED after target event");
    }

    @Test
    @DisplayName("EXISTENCE: Unrelated events should not affect status")
    void unrelatedEventsShouldNotAffectStatus() {
        // Given
        createExistenceConstraint("exist_A", "A");
        ConstraintStatus initialStatus = getConstraintStatus("exist_A");

        // When - send unrelated event
        sendEvent("B");
        sendEvent("C");

        // Then - status should remain unchanged
        assertEquals(initialStatus, getConstraintStatus("exist_A"),
                "EXISTENCE constraint should not change when unrelated events occur");
    }

    @Test
    @DisplayName("EXISTENCE: Multiple EXISTENCE constraints should be independent")
    void multipleConstraintsShouldBeIndependent() {
        // Given
        createExistenceConstraint("exist_A", "A");
        createExistenceConstraint("exist_B", "B");

        // When - only send A
        sendEvent("A");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("exist_A"),
                "exist_A should be FULFILLED");
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("exist_B"),
                "exist_B should remain TEMPORARY_VIOLATION");
    }

    @Test
    @DisplayName("EXISTENCE: Should remain FULFILLED after additional events")
    void shouldRemainFulfilledAfterAdditionalEvents() {
        // Given
        createExistenceConstraint("exist_A", "A");
        sendEvent("A");
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("exist_A"));

        // When - send more events
        sendEvent("B");
        sendEvent("A"); // send A again

        // Then - should still be fulfilled
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("exist_A"),
                "EXISTENCE should remain FULFILLED after additional events");
    }

    // Helper method to create EXISTENCE constraint
    private void createExistenceConstraint(String name, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.EXISTENCE,
                name,
                null, // timer
                null, // activationEventName
                new ConditionRequest("", "", "", null), // activationCondition
                targetEvent, // targetEventName
                new ConditionRequest("", "", "", null), // targetCondition
                null, // correlationCondition
                ConstraintStatus.TEMPORARY_VIOLATION, // initial status
                "task", // activationEventType
                "task" // targetEventType
        );
    }
}
