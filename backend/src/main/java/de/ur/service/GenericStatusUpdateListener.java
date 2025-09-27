package de.ur.service;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import de.ur.dao.ConstraintStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenericStatusUpdateListener implements UpdateListener {

    private final String constraintName;
    private final ConstraintStatus statusToSet;
    private final boolean removeOnUpdate;
    private final ConstraintService constraintService;
    private final EsperService esperService;

    public GenericStatusUpdateListener(String constraintName, ConstraintStatus statusToSet, boolean removeOnUpdate, ConstraintService constraintService, EsperService esperService) {
        this.constraintName = constraintName;
        this.statusToSet = statusToSet;
        this.removeOnUpdate = removeOnUpdate;
        this.constraintService = constraintService;
        this.esperService = esperService;
    }

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, com.espertech.esper.runtime.client.EPRuntime runtime) {
        if (newEvents == null || newEvents.length == 0) {
            return;
        }

        EventBean event = newEvents[0];

        if (event.get("timestamp") != null) {
            long startTime = (long) event.get("timestamp");
            long latencyNanos = System.nanoTime() - startTime;
            log.info("LATENCY,{},{}", this.constraintName, latencyNanos);
        }

        log.info("Reacting to '{}' for constraint '{}'", statusToSet, constraintName);
        constraintService.getConstraints().get(constraintName).updateStatus(statusToSet);

        if (removeOnUpdate) {
            esperService.removeConstraint(constraintName);
        }
    }
}