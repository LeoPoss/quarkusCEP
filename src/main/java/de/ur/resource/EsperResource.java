package de.ur.resource;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.compiler.client.EPCompileException;
import com.espertech.esper.runtime.client.EPDeployException;
import com.espertech.esper.runtime.client.EPStatement;
import de.ur.dao.SampleEvent;
import de.ur.service.EsperService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/esper")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EsperResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(EsperResource.class);

    @Inject
    EsperService esperService;

    @POST
    @Path("/deploy")
    public Response deployStatement(DeployStatementRequest request) {
        try {
            String deploymentId = UUID.randomUUID().toString();
            EPStatement statement = esperService.deployStatement(deploymentId, request.getEplStatement());
            String statementName = statement.getName();

            // Add a simple logging listener if requested
            if (request.isAddListener()) {
                esperService.addListener(deploymentId, statementName, (newEvents, oldEvents, s, r) -> {
                    if (newEvents != null) {
                        for (EventBean newEvent : newEvents) {
                            LOGGER.info("Event received: {}", newEvent.getUnderlying());
                        }
                    }
                });
            }

            Map<String, String> response = new HashMap<>();
            response.put("deploymentId", deploymentId);
            response.put("statementName", statement.getName());

            return Response.ok(response).build();
        } catch (EPCompileException | EPDeployException e) {
            LOGGER.error("Failed to deploy statement", e);
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/event")
    public Response sendEvent(SampleEvent event) {
        try {
            esperService.sendEvent(event);
            return Response.ok(Map.of("status", "Event sent successfully")).build();
        } catch (Exception e) {
            LOGGER.error("Failed to send event", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/deploy/{deploymentId}")
    public Response undeployStatement(@PathParam("deploymentId") String deploymentId) {
        boolean success = esperService.undeploy(deploymentId);
        if (success) {
            return Response.ok(Map.of("status", "Statement undeployed successfully")).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "Deployment not found or undeploy failed")).build();
        }
    }

    @Setter
    @Getter
    public static class DeployStatementRequest {
        private String eplStatement;
        private boolean addListener;
    }
}