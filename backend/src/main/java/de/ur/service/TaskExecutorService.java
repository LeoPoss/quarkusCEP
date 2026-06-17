package de.ur.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class TaskExecutorService {

    @Inject
    ConstraintService constraintService;

    @Inject
    EsperService esperService;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    public void executeTask(String taskName, String constraintName, Map<String, String> payload) {
        executeTask(taskName, constraintName, payload, true);
    }

    public void executeTask(String taskName, String constraintName, Map<String, String> payload, boolean injectEventOnComplete) {
        String msgStart = String.format("Started '%s' from constraint '%s'.", taskName, constraintName);
        
        log.info(msgStart);
        constraintService.logExecution("START", constraintName, taskName, msgStart, payload);

        executor.schedule(() -> {
            String msgEnd = String.format("Automatically completed task '%s'.", taskName);
            log.info(msgEnd);
            constraintService.logExecution("COMPLETE", constraintName, taskName, msgEnd, payload);

            if (injectEventOnComplete) {
                de.ur.dao.GenericEvent targetEvent = new de.ur.dao.GenericEvent(
                    UUID.randomUUID().toString(),
                    taskName,
                    System.currentTimeMillis(),
                    payload != null ? new HashMap<>(payload) : new HashMap<>()
                );
                
                constraintService.addToTrace(taskName, targetEvent.getPayload(), targetEvent.getTimestamp());
                esperService.sendEvent(targetEvent);
                log.info("Task '{}' event injected into Esper stream for constraint '{}'.", taskName, constraintName);
            }
        }, 2, TimeUnit.SECONDS);
    }
    
    @jakarta.annotation.PreDestroy
    public void cleanup() {
        executor.shutdown();
    }
}
