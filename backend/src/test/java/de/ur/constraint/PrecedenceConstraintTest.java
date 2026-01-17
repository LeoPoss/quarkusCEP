package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PRECEDENCE constraint type.
 * PRECEDENCE(A, B) = "A must have occurred before B can occur"
 * 
 * Note: PRECEDENCE uses a 1-second timer for permanent violation detection,
 * so some tests need to wait for the timer to fire.
 */
@QuarkusTest
class PrecedenceConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("PRECEDENCE: Should start in INIT state")
    void shouldStartInInitState() {
        // Given
        createPrecedenceConstraint("prec_AB", "A", "B");

        // Then
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("prec_AB"),
                "PRECEDENCE constraint should start in INIT state");
    }

    @Test
    @DisplayName("PRECEDENCE: Should be FULFILLED when A occurs before B")
    void shouldFulfillWhenActivationBeforeTarget() {
        // Given
        createPrecedenceConstraint("prec_AB", "A", "B");

        // When - send A then B
        sendEvent("A");
        sendEvent("B");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("prec_AB"),
                "PRECEDENCE should be FULFILLED when A occurs before B");
    }

    @Test
    @DisplayName("PRECEDENCE: Should be TEMPORARY_VIOLATION when B occurs without A")
    void shouldViolateTemporarilyWhenTargetWithoutActivation() {
        // Given
        createPrecedenceConstraint("prec_AB", "A", "B");

        // When - send B without A
        sendEvent("B");

        // Then - immediately goes to TEMP_VIO (permanent violation fires after timer)
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("prec_AB"),
                "PRECEDENCE should be TEMPORARY_VIOLATION when B occurs without A (immediate)");
    }

    @Test
    @DisplayName("PRECEDENCE: Should become PERMANENT_VIOLATION after timer (B without A)")
    void shouldBecomePermanentViolationAfterTimer() throws InterruptedException {
        // Given
        createPrecedenceConstraint("prec_AB", "A", "B");

        // When - send B without A
        sendEvent("B");

        // Wait for the 1-second timer to fire
        Thread.sleep(1500);

        // Then - should now be PERMANENT_VIOLATION
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("prec_AB"),
                "PRECEDENCE should be PERMANENT_VIOLATION after timer fires");
    }

    @Test
    @DisplayName("PRECEDENCE: Activation alone should not change state")
    void activationAloneShouldNotChangeState() {
        // Given
        createPrecedenceConstraint("prec_AB", "A", "B");

        // When - send only A
        sendEvent("A");

        // Then - should still be INIT (waiting for B to determine outcome)
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("prec_AB"),
                "PRECEDENCE should remain INIT after only activation");
    }

    @Test
    @DisplayName("PRECEDENCE: Multiple activations before target should still fulfill")
    void multipleActivationsBeforeTargetShouldFulfill() {
        // Given
        createPrecedenceConstraint("prec_AB", "A", "B");

        // When - send A multiple times, then B
        sendEvent("A");
        sendEvent("A");
        sendEvent("A");
        sendEvent("B");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("prec_AB"),
                "PRECEDENCE should be FULFILLED with multiple activations before target");
    }

    @Test
    @DisplayName("PRECEDENCE: Multiple constraints should be independent")
    void multipleConstraintsShouldBeIndependent() throws InterruptedException {
        // Given
        createPrecedenceConstraint("prec_AB", "A", "B");
        createPrecedenceConstraint("prec_CD", "C", "D");

        // When - fulfill first, violate second
        sendEvent("A");
        sendEvent("B"); // prec_AB fulfilled
        sendEvent("D"); // prec_CD violated (no C before D)

        // Wait for timer
        Thread.sleep(1500);

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("prec_AB"),
                "prec_AB should be FULFILLED");
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("prec_CD"),
                "prec_CD should be PERMANENT_VIOLATION");
    }

    // Helper method to create PRECEDENCE constraint
    private void createPrecedenceConstraint(String name, String activationEvent, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.PRECEDENCE,
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
