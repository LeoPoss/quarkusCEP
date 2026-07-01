package de.ur.service;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import de.ur.dao.ConstraintStatus;
import de.ur.dao.StatementType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenericStatusUpdateListener implements UpdateListener {

    private final String constraintName;
    private final ConstraintStatus statusToSet;
    private final boolean removeOnUpdate;
    private final ConstraintService constraintService;
    private final EsperService esperService;
    private final StatementType statementType;

    public GenericStatusUpdateListener(String constraintName, ConstraintStatus statusToSet,
                                        boolean removeOnUpdate, ConstraintService constraintService,
                                        EsperService esperService, StatementType statementType) {
        this.constraintName = constraintName;
        this.statusToSet = statusToSet;
        this.removeOnUpdate = removeOnUpdate;
        this.constraintService = constraintService;
        this.esperService = esperService;
        this.statementType = statementType;
    }

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement,
                       com.espertech.esper.runtime.client.EPRuntime runtime) {
        if (newEvents == null || newEvents.length == 0) {
            return;
        }

        EventBean event = newEvents[0];

       if (event.get("timestamp") != null
            && this.statementType != StatementType.PERMANENT_VIOLATION) {
            long ingestNanos = (long) event.get("timestamp");
            double latencyMs = (System.nanoTime() - ingestNanos) / 1_000_000.0;
            log.info("LATENCY,{},{},{}", this.constraintName, this.statementType, latencyMs);
        }

        log.info("Reacting to '{}' for constraint '{}'", statusToSet, constraintName);
        constraintService.getConstraints().get(constraintName).updateStatus(statusToSet);

        if (removeOnUpdate) {
            esperService.removeConstraint(constraintName);
        }
    }
}
