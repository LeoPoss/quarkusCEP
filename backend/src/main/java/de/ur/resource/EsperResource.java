package de.ur.resource;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPStatement;
import de.ur.dao.SampleEvent;
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
    public Response sendEvent(SampleEvent event) {
        try {
            log.info("Sending event: {}", event);
            esperService.sendEvent(event);
            return Response.ok(Map.of("status", "Event sent successfully")).build();
        } catch (Exception e) {
            log.error("Failed to send event", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/deploy/{deploymentId}")
    public Response undeployStatement(@PathParam("deploymentId") String deploymentId) {
        esperService.undeploy(deploymentId);
        return Response.ok(Map.of("status", "Statement undeployed successfully")).build();
    }

    @Setter
    @Getter
    public static class DeployStatementRequest {
        private String eplStatement;
        private boolean addListener;
    }
}