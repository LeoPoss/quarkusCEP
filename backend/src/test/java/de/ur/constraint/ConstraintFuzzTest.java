package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fuzz/Property-based tests using random event sequences to check invariants.
 * Invariants checked:
 * 1. Monotonicity: Once PERMANENT_VIOLATION, always PERMANENT_VIOLATION.
 * 2. Stability: No unexpected exceptions.
 * 3. Definition: Status is always valid (INIT, ACTIVE, FULFILLED, VIOLATION).
 */
@QuarkusTest
@Tag("fuzz")
class ConstraintFuzzTest extends BaseConstraintTest {

    private final Random random = new Random();

    @RepeatedTest(50)
    @DisplayName("Fuzz ALTERNATE_PRECEDENCE with random A/B/Noise events")
    void fuzzAlternatePrecedence() throws InterruptedException {
        String constraintName = "fuzz_alt_prec_" + random.nextInt(10000);
        // Setup ALTERNATE_PRECEDENCE(A, B)
        constraintService.setupConstraint(
                ConstraintType.ALTERNATE_PRECEDENCE,
                constraintName,
                null,
                "A", new ConditionRequest("", "", "", null),
                "B", new ConditionRequest("", "", "", null),
                null,
                ConstraintStatus.INIT,
                "task", "task");

        ConstraintStatus currentStatus = ConstraintStatus.INIT;

        // Generate random sequence of 20 events
        for (int i = 0; i < 20; i++) {
            // 0=A, 1=B, 2=Noise, 3=Sleep
            int action = random.nextInt(4);

            if (action == 0) {
                sendEvent("A");
            } else if (action == 1) {
                sendEvent("B");
            } else if (action == 2) {
                sendEvent("NOISE_" + i);
            } else {
                Thread.sleep(10); // Check timing/race conds
            }

            ConstraintStatus newStatus = getConstraintStatus(constraintName);

            // Invariant: Validation
            assertNotNull(newStatus, "Status should never be null");

            // Invariant: Monotonicity for Violation
            if (currentStatus == ConstraintStatus.PERMANENT_VIOLATION) {
                assertEquals(ConstraintStatus.PERMANENT_VIOLATION, newStatus,
                        "Once violated, it should remain violated (Monotonicity)");
            }

            currentStatus = newStatus;
        }
    }

    @RepeatedTest(20)
    @DisplayName("Fuzz RESPONSE with random A/B/Noise events")
    void fuzzResponse() {
        String constraintName = "fuzz_resp_" + random.nextInt(10000);
        // Setup RESPONSE(A, B)
        constraintService.setupConstraint(
                ConstraintType.RESPONSE,
                constraintName,
                null,
                "A", new ConditionRequest("", "", "", null),
                "B", new ConditionRequest("", "", "", null),
                null,
                ConstraintStatus.INIT,
                "task", "task");

        for (int i = 0; i < 20; i++) {
            int action = random.nextInt(3); // A, B, Noise
            if (action == 0)
                sendEvent("A");
            else if (action == 1)
                sendEvent("B");
            else
                sendEvent("NOISE");

            ConstraintStatus newStatus = getConstraintStatus(constraintName);
            assertNotNull(newStatus);

            // RESPONSE doesn't have Permanent Violation on simple events usually,
            // but if we had timers it might.
            // We just verify it doesn't crash given weird sequences.
        }
    }
}
