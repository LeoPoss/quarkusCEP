package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NOT_PRECEDENCE constraint type.
 * NOT_PRECEDENCE(A, B) = "A must not have occurred before B"
 */
@QuarkusTest
class NotPrecedenceConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("NOT_PRECEDENCE: Should start in INIT state")
    void shouldStartInInitState() {
        // Given
        createNotPrecedenceConstraint("not_prec_AB", "A", "B");

        // Then
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("not_prec_AB"),
                "NOT_PRECEDENCE should start in INIT state");
    }

    @Test
    @DisplayName("NOT_PRECEDENCE: Should be FULFILLED when B occurs without A before")
    void shouldFulfillWhenTargetWithoutActivationBefore() {
        // Given
        createNotPrecedenceConstraint("not_prec_AB", "A", "B");

        // When - B without A before
        sendEvent("B");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("not_prec_AB"),
                "NOT_PRECEDENCE should be FULFILLED when B occurs without A before");
    }

    @Test
    @DisplayName("NOT_PRECEDENCE: Should VIOLATE when A occurs before B")
    void shouldViolateWhenActivationBeforeTarget() {
        // Given
        createNotPrecedenceConstraint("not_prec_AB", "A", "B");

        // When - A then B
        sendEvent("A");
        sendEvent("B");

        // Wait for timer (handler uses timer:interval(1 sec))
        org.awaitility.Awaitility.await()
                .atMost(2, java.util.concurrent.TimeUnit.SECONDS)
                .pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("not_prec_AB"),
                            "NOT_PRECEDENCE should be PERMANENT_VIOLATION when A occurs before B");
                });
    }

    @Test
    @DisplayName("NOT_PRECEDENCE: Activation alone should remain INIT (no temp violation tracking)")
    void activationAloneShouldRemainInit() {
        // Given
        createNotPrecedenceConstraint("not_prec_AB", "A", "B");

        // When - only A (could be dangerous if B comes after)
        sendEvent("A");

        // Then - handler doesn't track temporary violation, stays INIT
        // NOTE: This could be considered a limitation - ideally would be TEMP_VIOLATION
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("not_prec_AB"),
                "NOT_PRECEDENCE stays INIT after A (handler doesn't track temp violation)");
    }

    @Test
    @DisplayName("NOT_PRECEDENCE: B without prior A remains fulfilled")
    void targetWithoutPriorActivationRemainsFulfilled() {
        // Given
        createNotPrecedenceConstraint("not_prec_AB", "A", "B");

        // When
        sendEvent("B"); // fulfilled
        sendEvent("C");
        sendEvent("D");

        // Then - still fulfilled
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("not_prec_AB"),
                "NOT_PRECEDENCE should remain FULFILLED");
    }

    // Helper method
    private void createNotPrecedenceConstraint(String name, String activationEvent, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.NOT_PRECEDENCE,
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
