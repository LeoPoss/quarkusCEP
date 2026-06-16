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
        String execId = UUID.randomUUID().toString().substring(0, 8);
        String msgStart = String.format("Task '%s' [ID: %s] started execution.", taskName, execId);
        
        log.info(msgStart);
        constraintService.logExecution("START", constraintName, taskName, msgStart, payload);

        // Simulate background execution
        executor.schedule(() -> {
            String msgEnd = String.format("Task '%s' [ID: %s] completed successfully (Status: 200 OK).", taskName, execId);
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
                log.info("Task '{}' [ID: {}] event injected into Esper stream.", taskName, execId);
            }
        }, 2, TimeUnit.SECONDS);
    }
    
    @jakarta.annotation.PreDestroy
    public void cleanup() {
        executor.shutdown();
    }
}
