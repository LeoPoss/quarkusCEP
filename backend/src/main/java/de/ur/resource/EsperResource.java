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
import java.util.Map;
import java.util.UUID;

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
    public Response sendEvent(GenericEvent event) {
        try {
            log.info("Received event: {}", event);
            esperService.sendEvent(event);
            return Response.ok(Map.of("status", "Event sent successfully")).build();
        } catch (Exception e) {
            log.error("Failed to send event", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of("error", e.getMessage())).build();
        }
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