package de.ur.resource;

import de.ur.dao.Constraint;
import de.ur.service.ConstraintService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/analysis")
@ApplicationScoped
@Slf4j
@Produces(MediaType.APPLICATION_JSON)
public class AnalysisResource {

    @Inject
    ConstraintService constraintService;

    @Inject
    WhatIfAnalyzer analyzer;

    @GET
    @Path("/allowed-tasks")
    public Response getAllowedTasks() {
        List<Constraint> constraints = new ArrayList<>(constraintService.getConstraints().values());
        Set<String> possibleEvents = constraintService.getKnownEvents();
        List<String> trace = constraintService.getTrace();

        log.debug("--- Analysis (Allowed Tasks) ---");

        Map<String, Boolean> taskAnalysisMap = analyzer.analyzeAllowedTasks(constraints, possibleEvents, trace);

        List<TaskAnalysisResponse> responseBody = taskAnalysisMap.entrySet().stream()
                .map(entry -> new TaskAnalysisResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        log.debug("\n--- Summary (Allowed Tasks) ---");
        responseBody.forEach(task -> log.debug("Event '{}': {}", task.task, task.isUnsafe));

        return Response.ok(responseBody).build();
    }


    @GET
    @Path("/finishability")
    public Response getFinishability() {
        List<Constraint> constraints = new ArrayList<>(constraintService.getConstraints().values());

        log.debug("\n--- Analysis (Finishability) ---");

        Map<String, Object> finishAnalysisMap = analyzer.checkFinishability(constraints);

        FinishabilityResponse responseBody = new FinishabilityResponse(
                (Boolean) finishAnalysisMap.get("canFinish"),
                (List<String>) finishAnalysisMap.get("reasons")
        );

        log.debug("\n--- Summary (Finishability) ---");
        if (responseBody.canFinish) {
            log.debug("Result: Process can be successfully finished.");
        } else {
            log.debug("Result: Process CANNOT be finished yet.");
            log.debug("Reasons:");
            responseBody.reasons.forEach(reason -> log.debug("- " + reason));
        }

        return Response.ok(responseBody).build();
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskAnalysisResponse {
        public String task;
        public boolean isUnsafe;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FinishabilityResponse {
        public boolean canFinish;
        public List<String> reasons; // Will be empty if canFinish is true
    }
}