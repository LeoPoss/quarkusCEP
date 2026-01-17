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

@QuarkusTest
public abstract class BaseConstraintTest {

    @Inject
    protected EsperService esperService;

    @Inject
    protected ConstraintService constraintService;

    @BeforeEach
    void resetEsper() {
        esperService.reset();
        constraintService.resetConstraints();
        constraintService.getTrace().clear();
    }
    protected void sendEvent(String eventType) {
        sendEvent(eventType, null);
    }

    protected void sendEvent(String eventType, Map<String, String> payload) {
        GenericEvent event = new GenericEvent(
                UUID.randomUUID().toString(),
                eventType,
                System.nanoTime(),
                payload);

        if (constraintService.getKnownEvents().contains(eventType)) {
            constraintService.addToTrace(eventType, payload);
        }

        esperService.sendEvent(event);
    }

    protected ConstraintStatus getConstraintStatus(String constraintName) {
        var constraint = constraintService.getConstraints().get(constraintName);
        return constraint != null ? constraint.getStatus() : null;
    }

    protected void waitForProcessing() throws InterruptedException {
        Thread.sleep(100);
    }
}
