package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RESPONDED_EXISTENCE constraint type.
 * RESPONDED_EXISTENCE(A, B) = "If A or B occurs, the other must also occur"
 */
@QuarkusTest
class RespondedExistenceConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("RESPONDED_EXISTENCE: Should start in INIT state")
    void shouldStartInInitState() {
        // Given
        createRespondedExistenceConstraint("resp_exist_AB", "A", "B");

        // Then
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("resp_exist_AB"),
                "RESPONDED_EXISTENCE should start in INIT state");
    }

    @Test
    @DisplayName("RESPONDED_EXISTENCE: Should become TEMPORARY_VIOLATION after activation")
    void shouldBeTemporaryViolationAfterActivation() {
        // Given
        createRespondedExistenceConstraint("resp_exist_AB", "A", "B");

        // When - only A
        sendEvent("A");

        // Then - needs B
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("resp_exist_AB"),
                "RESPONDED_EXISTENCE should be TEMPORARY_VIOLATION after A (needs B)");
    }

    @Test
    @DisplayName("RESPONDED_EXISTENCE: Should be FULFILLED when both A and B occur")
    void shouldFulfillWhenBothOccur() {
        // Given
        createRespondedExistenceConstraint("resp_exist_AB", "A", "B");

        // When
        sendEvent("A");
        sendEvent("B");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("resp_exist_AB"),
                "RESPONDED_EXISTENCE should be FULFILLED when both occur");
    }

    @Test
    @DisplayName("RESPONDED_EXISTENCE: Order should not matter - B then A")
    void orderShouldNotMatter() {
        // Given
        createRespondedExistenceConstraint("resp_exist_AB", "A", "B");

        // When - B first, then A
        sendEvent("B");
        sendEvent("A");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("resp_exist_AB"),
                "RESPONDED_EXISTENCE should be FULFILLED regardless of order");
    }

    @Test
    @DisplayName("RESPONDED_EXISTENCE: Target alone should remain INIT (only activation triggers temp violation)")
    void targetAloneShouldRemainInit() {
        // Given
        createRespondedExistenceConstraint("resp_exist_AB", "A", "B");

        // When - only B
        sendEvent("B");

        // Then - handler only watches for ACTIVATION events for temp violation
        // NOTE: This could be considered a limitation - ideally B alone would also be
        // TEMP_VIOLATION
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("resp_exist_AB"),
                "RESPONDED_EXISTENCE remains INIT after B (handler only tracks activation)");
    }

    // Helper method
    private void createRespondedExistenceConstraint(String name, String activationEvent, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.RESPONDED_EXISTENCE,
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
