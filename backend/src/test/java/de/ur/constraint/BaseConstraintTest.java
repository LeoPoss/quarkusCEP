package de.ur.constraint;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.GenericEvent;
import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;
import java.util.UUID;

/**
 * Base class for constraint tests, providing common setup and utility methods.
 */
@QuarkusTest
public abstract class BaseConstraintTest {

    @Inject
    protected EsperService esperService;

    @Inject
    protected ConstraintService constraintService;

    @BeforeEach
    void resetEsper() {
        // Reset Esper and constraints before each test
        esperService.reset();
        constraintService.resetConstraints();
        constraintService.getTrace().clear();
    }

    /**
     * Send an event to the Esper runtime.
     */
    protected void sendEvent(String eventType) {
        sendEvent(eventType, null);
    }

    /**
     * Send an event with payload to the Esper runtime.
     */
    protected void sendEvent(String eventType, Map<String, String> payload) {
        GenericEvent event = new GenericEvent(
                UUID.randomUUID().toString(),
                eventType,
                System.nanoTime(),
                payload);

        // Add to trace if this is a known task event
        if (constraintService.getKnownEvents().contains(eventType)) {
            constraintService.addToTrace(eventType, payload);
        }

        esperService.sendEvent(event);
    }

    /**
     * Get the current status of a constraint by name.
     */
    protected ConstraintStatus getConstraintStatus(String constraintName) {
        var constraint = constraintService.getConstraints().get(constraintName);
        return constraint != null ? constraint.getStatus() : null;
    }

    /**
     * Wait a bit for Esper to process events (some patterns are async).
     */
    protected void waitForProcessing() throws InterruptedException {
        Thread.sleep(100);
    }
}
