package de.ur.resource;

import de.ur.service.ContextAnalysisService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/analysis")
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
public class AnalysisResource {
    private final ContextAnalysisService analysisService;

    @GET
    @Path("/allowed-tasks")
    public Response getAllowedTasks() {
        var taskAnalysis = analysisService.analyzeAllowedTasks();
        return Response.ok(taskAnalysis).build();
    }

    @GET
    @Path("/finishability")
    public Response getFinishability() {
        var response = analysisService.checkFinishability();
        return Response.ok(response).build();
    }

    @GET
    @Path("/trace")
    public Response getTrace() {
        return Response.ok(analysisService.getTrace()).build();
    }
}
