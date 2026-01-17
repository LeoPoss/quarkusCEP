package de.ur.service.constraint;

import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dto.ConditionRequest;
import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@QuarkusTest
public class PayloadConstraintTest {

    @Inject
    ConstraintService constraintService;

    @Inject
    EsperService esperService;

    @Test
    public void testConstraintWithPayloadCondition() {
        // Reproduce the user's scenario:
        // Constraint with a payload condition (e.g., cast(payload('user'), double) = 3)

        String constraintName = "payload_test_constraint";
        ConditionRequest activationCondition = new ConditionRequest("user", "=", "3", null);

        // This should not throw EPCompileException
        assertDoesNotThrow(() -> {
            constraintService.setupConstraint(
                    ConstraintType.RESPONSE,
                    constraintName,
                    null,
                    "A",
                    activationCondition,
                    "B",
                    null,
                    null,
                    de.ur.dao.ConstraintStatus.INIT,
                    "task",
                    "task");
        });

        // Cleanup
        esperService.removeConstraint(constraintName);
    }

    @Test
    public void testConstraintWithCorrelation() {
        String constraintName = "correlation_test_constraint";
        CorrelationCondition correlation = new CorrelationCondition("user", "=", "user");

        assertDoesNotThrow(() -> {
            constraintService.setupConstraint(
                    ConstraintType.RESPONSE,
                    constraintName,
                    null,
                    "A",
                    null,
                    "B",
                    null,
                    correlation,
                    de.ur.dao.ConstraintStatus.INIT,
                    "task",
                    "task");
        });

        // Cleanup
        esperService.removeConstraint(constraintName);
    }
}
