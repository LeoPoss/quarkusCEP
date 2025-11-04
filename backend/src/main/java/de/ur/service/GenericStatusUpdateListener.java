package de.ur.service;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import de.ur.dao.Constraint;
import de.ur.dao.ConstraintStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

//        if (event.get("timestamp") != null) {
//            long startTime = (long) event.get("timestamp");
//            long latencyNanos = System.nanoTime() - startTime;
//            log.info("LATENCY,{},{}", this.constraintName, latencyNanos);
//        }


        if (event.get("test") != null && !(boolean) event.get("test")) {
            log.info("Reacting to '{}' for constraint '{}'", statusToSet, constraintName);
            constraintService.getConstraints().get(constraintName).updateStatus(statusToSet);
            if (removeOnUpdate) {
                esperService.removeConstraint(constraintName);
            }
        } else {
            log.info("Reacting to TEST event '{}' setting constraint '{}' to '{}'", event.get("id"), constraintName, statusToSet);
            ConcurrentHashMap<String, Constraint> copiedConstraints =
                    constraintService.getConstraints().entrySet().stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            Map.Entry::getKey,
                                            entry -> new Constraint(entry.getValue()),
                                            (existing, replacement) -> existing,
                                            ConcurrentHashMap::new
                                    )
                            );
            if (copiedConstraints.containsKey(constraintName)) {
                copiedConstraints.get(constraintName).updateStatus(statusToSet);

                ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, String>>> eventConstraintMap =
                        constraintService.getConstraintByEventTest();
                String eventId = event.get("id").toString();
                String constraintKey = constraintName;

                Map<String, String> newStatusMap = copiedConstraints.values().stream()
                        .collect(Collectors.toMap(
                                Constraint::getName,
                                constraint -> constraint.getStatus().toString()
                        ));

                ConcurrentHashMap<String, Map<String, String>> innerMap =
                        eventConstraintMap.computeIfAbsent(
                                eventId,
                                k -> new ConcurrentHashMap<>()
                        );

                innerMap.put(constraintKey, newStatusMap);

                copiedConstraints.forEach((key, value) -> System.out.println(key + " -> " + value.getStatus()));


                // Wir kriegen geloggt eventID unique und änderungen die dadurch auftreten
                // mit Event wenn Test, variable anlegen hashmap mit id und dann hier zugreifen und updaten
            }

        }

    }
}