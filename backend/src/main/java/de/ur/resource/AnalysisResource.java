package de.ur.resource;

import de.ur.dto.AllowedTaskResponse;
import de.ur.dto.FinishabilityResponse;
import de.ur.dto.TaskAnalysisResponse;
import de.ur.service.AnalyzerService;
import de.ur.service.ConstraintService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Path("/analysis")
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
public class AnalysisResource {
    private final ConstraintService constraintService;
    private final AnalyzerService analyzer;

    @GET
    @Path("/allowed-tasks")
    public Response getAllowedTasks() {
        var constraints = constraintService.getConstraints().values().stream().toList();
        var taskAnalysis = analyzer.analyzeAllowedTasks(
                constraints,
                constraintService.getKnownEvents(),
                constraintService.getTrace()
        );
        
        return Response.ok(taskAnalysis).build();
    }

    @GET
    @Path("/finishability")
    public Response getFinishability() {
        var constraints = constraintService.getConstraints().values().stream().toList();
        var analysisResult = analyzer.checkFinishability(constraints);

        var response = new FinishabilityResponse((Boolean) analysisResult.get("canFinish"), (List<String>) analysisResult.get("reasons"));
        return Response.ok(response).build();
    }
}