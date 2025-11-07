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
    @Path("/existence")
    public Response createExistenceConstraint(ConstraintRequest request) {
        log.info(request.toString());
        constraintService.setupConstraint(
                ConstraintType.EXISTENCE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.TEMPORARY_VIOLATION,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/notexistence")
    public Response createNotExistenceConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.NOT_EXISTENCE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/response")
    public Response createResponseConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.RESPONSE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/respondedexistence")
    public Response createRespondedExistenceConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.RESPONDED_EXISTENCE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/alternateresponse")
    public Response createAlternateResponseConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.ALTERNATE_RESPONSE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/chainresponse")
    public Response createChainResponseConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.CHAIN_RESPONSE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/precedence")
    public Response createPrecedenceConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.PRECEDENCE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/alternateprecedence")
    public Response createAlternatePrecedenceConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.ALTERNATE_PRECEDENCE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/chainprecedence")
    public Response createChainPrecedenceConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.CHAIN_PRECEDENCE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/notresponse")
    public Response createNotResponseConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.NOT_RESPONSE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }

    @POST
    @Path("/notprecedence")
    public Response createNotPrecedenceConstraint(ConstraintRequest request) {
        constraintService.setupConstraint(
                ConstraintType.NOT_PRECEDENCE,
                request.name(),
                request.timer(),
                request.activationEvent(),
                request.activationCondition(),
                request.targetEvent(),
                request.targetCondition(),
                request.correlationCondition(),
                ConstraintStatus.INIT,
                request.activationEventType(),
                request.targetEventType()
        );
        return Response.created(URI.create(request.name())).build();
    }
}
