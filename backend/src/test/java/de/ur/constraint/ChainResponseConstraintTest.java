package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHAIN_RESPONSE constraint type.
 * CHAIN_RESPONSE(A, B) = "B must immediately follow A"
 */
@QuarkusTest
class ChainResponseConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("CHAIN_RESPONSE: Should start in INIT state")
    void shouldStartInInitState() {
        // Given
        createChainResponseConstraint("chain_AB", "A", "B");

        // Then
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("chain_AB"),
                "CHAIN_RESPONSE should start in INIT state");
    }

    @Test
    @DisplayName("CHAIN_RESPONSE: Should become TEMPORARY_VIOLATION after activation")
    void shouldBeTemporaryViolationAfterActivation() {
        // Given
        createChainResponseConstraint("chain_AB", "A", "B");

        // When
        sendEvent("A");

        // Then
        assertEquals(ConstraintStatus.TEMPORARY_VIOLATION, getConstraintStatus("chain_AB"),
                "CHAIN_RESPONSE should be TEMPORARY_VIOLATION after activation");
    }

    @Test
    @DisplayName("CHAIN_RESPONSE: Should be FULFILLED when B immediately follows A")
    void shouldFulfillWhenTargetImmediatelyFollows() {
        // Given
        createChainResponseConstraint("chain_AB", "A", "B");

        // When - B immediately follows A
        sendEvent("A");
        sendEvent("B");

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("chain_AB"),
                "CHAIN_RESPONSE should be FULFILLED when B immediately follows A");
    }

    @Test
    @DisplayName("CHAIN_RESPONSE: Should be PERMANENT_VIOLATION when other event follows A")
    void shouldViolateWhenOtherEventFollowsActivation() {
        // Given
        createChainResponseConstraint("chain_AB", "A", "B");

        // When - C follows A (not B)
        sendEvent("A");
        sendEvent("C"); // wrong event!

        // Then
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("chain_AB"),
                "CHAIN_RESPONSE should be PERMANENT_VIOLATION when non-target follows activation");
    }

    @Test
    @DisplayName("CHAIN_RESPONSE: Target before activation should not affect")
    void targetBeforeActivationShouldNotAffect() {
        // Given
        createChainResponseConstraint("chain_AB", "A", "B");

        // When - B occurs first (no activation yet)
        sendEvent("B");

        // Then - still INIT
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("chain_AB"),
                "CHAIN_RESPONSE should remain INIT when target occurs before activation");
    }

    @Test
    @DisplayName("CHAIN_RESPONSE: Multiple constraints should be independent")
    void multipleConstraintsShouldBeIndependent() {
        // Given
        createChainResponseConstraint("chain_AB", "A", "B");
        createChainResponseConstraint("chain_CD", "C", "D");

        // When - fulfill first, violate second
        sendEvent("A");
        sendEvent("B"); // chain_AB fulfilled
        sendEvent("C");
        sendEvent("X"); // chain_CD violated (X instead of D)

        // Then
        assertEquals(ConstraintStatus.FULFILLED, getConstraintStatus("chain_AB"),
                "chain_AB should be FULFILLED");
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("chain_CD"),
                "chain_CD should be PERMANENT_VIOLATION");
    }

    // Helper method
    private void createChainResponseConstraint(String name, String activationEvent, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.CHAIN_RESPONSE,
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
