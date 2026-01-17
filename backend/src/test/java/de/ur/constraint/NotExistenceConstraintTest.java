package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NOT_EXISTENCE constraint type.
 * NOT_EXISTENCE(A) = "A must never occur"
 */
@QuarkusTest
class NotExistenceConstraintTest extends BaseConstraintTest {

    @Test
    @DisplayName("NOT_EXISTENCE: Should start in INIT state")
    void shouldStartInInitState() {
        // Given
        createNotExistenceConstraint("not_exist_A", "A");

        // Then
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("not_exist_A"),
                "NOT_EXISTENCE constraint should start in INIT state");
    }

    @Test
    @DisplayName("NOT_EXISTENCE: Should become PERMANENT_VIOLATION when forbidden event occurs")
    void shouldViolateWhenForbiddenEventOccurs() {
        // Given
        createNotExistenceConstraint("not_exist_A", "A");

        // When - send forbidden event
        sendEvent("A");

        // Then
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("not_exist_A"),
                "NOT_EXISTENCE should be PERMANENT_VIOLATION when forbidden event occurs");
    }

    @Test
    @DisplayName("NOT_EXISTENCE: Unrelated events should not affect status")
    void unrelatedEventsShouldNotAffect() {
        // Given
        createNotExistenceConstraint("not_exist_A", "A");

        // When - send unrelated events
        sendEvent("B");
        sendEvent("C");
        sendEvent("D");

        // Then - still INIT
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("not_exist_A"),
                "NOT_EXISTENCE should remain INIT when unrelated events occur");
    }

    @Test
    @DisplayName("NOT_EXISTENCE: Multiple NOT_EXISTENCE constraints should be independent")
    void multipleConstraintsShouldBeIndependent() {
        // Given
        createNotExistenceConstraint("not_exist_A", "A");
        createNotExistenceConstraint("not_exist_B", "B");

        // When - only send A
        sendEvent("A");

        // Then
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("not_exist_A"),
                "not_exist_A should be PERMANENT_VIOLATION");
        assertEquals(ConstraintStatus.INIT, getConstraintStatus("not_exist_B"),
                "not_exist_B should remain INIT");
    }

    @Test
    @DisplayName("NOT_EXISTENCE: Violation is permanent and cannot be recovered")
    void violationIsPermanent() {
        // Given
        createNotExistenceConstraint("not_exist_A", "A");
        sendEvent("A"); // violate
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("not_exist_A"));

        // When - send other events
        sendEvent("B");
        sendEvent("C");

        // Then - still permanently violated
        assertEquals(ConstraintStatus.PERMANENT_VIOLATION, getConstraintStatus("not_exist_A"),
                "NOT_EXISTENCE violation should remain permanent");
    }

    // Helper method
    private void createNotExistenceConstraint(String name, String targetEvent) {
        constraintService.setupConstraint(
                ConstraintType.NOT_EXISTENCE,
                name,
                null,
                null, // no activation event
                new ConditionRequest("", "", "", null),
                targetEvent,
                new ConditionRequest("", "", "", null),
                null,
                ConstraintStatus.INIT,
                "task",
                "task");
    }
}
