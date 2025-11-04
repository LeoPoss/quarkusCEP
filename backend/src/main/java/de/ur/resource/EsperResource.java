package de.ur.resource;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPStatement;
import de.ur.dao.GenericEvent;
import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/esper")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class EsperResource {

    @Inject
    EsperService esperService;

    @Inject
    ConstraintService constraintService;

    @GET
    public Response getDeployments() {
        return Response.ok(esperService.getRuntime().getDeploymentService().getDeployments()).build();
    }

    @POST
    @Path("/deploy")
    public Response deployStatement(DeployStatementRequest request) {
        String deploymentId = UUID.randomUUID().toString();
        EPStatement statements = esperService.deployStatements(deploymentId, request.getEplStatement());
        String statementName = statements.getName();

        if (request.isAddListener()) {
            esperService.addListener(deploymentId, statementName, (newEvents, oldEvents, s, r) -> {
                if (newEvents != null) {
                    for (EventBean newEvent : newEvents) {
                        log.info("Event received: {}", newEvent.getUnderlying());
                    }
                }
            });
        }

        Map<String, String> response = new HashMap<>();
        response.put("deploymentId", deploymentId);
        response.put("statementName", statements.getName());

        return Response.ok(response).build();
    }

    @POST
    @Path("/event")
    public Response sendEvent(Map<String, Object> event) {
        try {
            log.info("Received event: {}", event);
            var createdEvent = processSingleEvent(event);
            return Response.ok(createdEvent).build();
        } catch (Exception e) {
            log.error("Failed to send event", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }


    @POST
    @Path("/events/batch")
    public Response receiveEventBatch(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Event list cannot be null or empty")).build();
        }
        try {
            log.debug("Received event batch of size: {}", events.size());
            // Loop through the list and process each event
            for (Map<String, Object> event : events) {
                processSingleEvent(event);
            }
            return Response.ok(Map.of("status", "Batch of " + events.size() + " events sent successfully")).build();
        } catch (Exception e) {
            log.error("Failed to process event batch", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    private GenericEvent processSingleEvent(Map<String, Object> event) {
        Map<String, String> payload = null;
        Object payloadObj = event.get("payload");

        if (payloadObj instanceof Map<?, ?> map) {
            payload = map.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().toString(),
                            e -> e.getValue() != null ? e.getValue().toString() : null
                    ));
        }

        boolean isTest = event.get("test") != null && Boolean.parseBoolean(event.get("test").toString());
        String eventUuid = UUID.randomUUID().toString();

        GenericEvent genericEvent = new GenericEvent(
                eventUuid,
                String.valueOf(event.get("eventType")),
                System.nanoTime(),
                isTest,
                payload
        );

        esperService.sendEvent(genericEvent);

        return genericEvent;
    }

    @POST
    @Path("/testEvent")
    public Response testEvent(Map<String, Object> event) {
        log.info("Received TEST event: {}", event);
        var sentEvent = processSingleEvent(event);
        var testEventId = sentEvent.getId();

        try {
            // Wait for all listeners to (hopefully) finish
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        var eventResults = constraintService.getConstraintByEventTest().get(testEventId);

        var isPermanentlyViolated = false;

        if (eventResults != null) {
            isPermanentlyViolated = eventResults.values().stream()
                    .flatMap(constraint -> constraint.values().stream())
                    .anyMatch("PERMANENT_VIOLATION"::equals);
        }

        if (isPermanentlyViolated) {
            // Event execution should be blocked
            System.out.println("TEST FAILED: Event " + testEventId + " would cause a PERMANENT_VIOLATION.");
        } else {
            // Event execution is safe (relative to permanent violations)
            System.out.println("TEST PASSED: Event " + testEventId + " is safe.");
        }

        return Response.ok(isPermanentlyViolated).build();
    }

    @POST
    @Path("/reset")
    public Response resetEsper() {
        try {
            esperService.reset();
            constraintService.resetConstraints();
            return Response.ok(Map.of(
                    "status", "Esper engine has been reset and reinitialized",
                    "timestamp", java.time.Instant.now()
            )).build();
        } catch (Exception e) {
            log.error("Failed to reset Esper engine", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "Failed to reset Esper engine: " + e.getMessage(),
                            "timestamp", java.time.Instant.now()
                    ))
                    .build();
        }
    }

    @Setter
    @Getter
    public static class DeployStatementRequest {
        private String eplStatement;
        private boolean addListener;
    }
}