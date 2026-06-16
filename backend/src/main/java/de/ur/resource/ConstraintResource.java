package de.ur.resource;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConstraintRequest;
import de.ur.service.ConstraintService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Path("/constraints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequiredArgsConstructor
public class ConstraintResource {
    private final ConstraintService constraintService;

    @GET
    public Response getAllConstraints() {
        return Response.ok(constraintService.getConstraints().values()).build();
    }

    @POST
    @Path("/{type}")
    public Response createConstraint(@PathParam("type") String type, ConstraintRequest request) {
        ConstraintType constraintType = parseConstraintType(type);
        if (constraintType == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Unknown constraint type: " + type)
                    .build();
        }

        ConstraintStatus initialStatus = constraintType == ConstraintType.EXISTENCE
                ? ConstraintStatus.TEMPORARY_VIOLATION
                : ConstraintStatus.INIT;

        log.info("Creating {} constraint: {}", type, request);
        log.info("autoExecute raw value: {} (class: {})", request.autoExecute(), request.autoExecute() != null ? request.autoExecute().getClass().getName() : "null");
        boolean autoExec = request.autoExecute() != null && request.autoExecute();
        log.info("autoExecute resolved to: {}", autoExec);
        constraintService.setupConstraint(
                        constraintType,
                        request.name(),
                        request.timer(),
                        request.activationEvent(),
                        request.activationCondition(),
                        request.targetEvent(),
                        request.targetCondition(),
                        request.correlationCondition(),
                        initialStatus,
                        request.activationEventType(),
                        request.targetEventType(),
                        autoExec,
                        request.autoExecutePayload()
                );
        return Response.created(URI.create(request.name())).build();
    }

    private static ConstraintType parseConstraintType(String type) {
        return switch (type.toLowerCase()) {
            case "existence" -> ConstraintType.EXISTENCE;
            case "notexistence" -> ConstraintType.NOT_EXISTENCE;
            case "response" -> ConstraintType.RESPONSE;
            case "respondedexistence" -> ConstraintType.RESPONDED_EXISTENCE;
            case "alternateresponse" -> ConstraintType.ALTERNATE_RESPONSE;
            case "chainresponse" -> ConstraintType.CHAIN_RESPONSE;
            case "precedence" -> ConstraintType.PRECEDENCE;
            case "alternateprecedence" -> ConstraintType.ALTERNATE_PRECEDENCE;
            case "chainprecedence" -> ConstraintType.CHAIN_PRECEDENCE;
            case "notresponse" -> ConstraintType.NOT_RESPONSE;
            case "notprecedence" -> ConstraintType.NOT_PRECEDENCE;
            default -> null;
        };
    }
}
