package de.ur.resource;


import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Path("/constraint")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class ConstraintResource {

    @Inject
    EsperService esperService;

    @Inject
    ConstraintService constraintService;

    @GET
    public Response getAllConstraints() {
        for (var d : esperService.getRuntime().getDeploymentService().getDeployments()) {
            log.info("Deployment: {}", d);
        }
        return Response.ok(constraintService.getConstraints()).build();
    }

    // Prozessstart und Ende -> Muss auch in Paper, Prozess kann nicht beendet werden, solange nicht alle constraints erfüllt

    // INSERT INTO ist quasi reaction auf FROM PATTERN
    // SELECT triggert dann listener der code ausführt, deswegen zwei Ebenen

    // TODO: Wann können wir löschen, also welche status sind final und welche nicht
    // TODO: Evtl. nicht constraints löschen sondern alte activation und target

    @POST
    @Path("/existence")
    public Response createExistenceConstraint(ConstraintRequest request) {
        constraintService.createExistenceActivationQuery(request.name, request.targetEvent);

        constraintService.createExistenceFulfillmentQuery(request.name);

        // Welche Events brauchen wir?
        // 1. Activation detecten und nach constraint status

        // 2. Activation von constraints status -> Status update

        // temp vio -> fulfilled

        return Response.created(URI.create(request.name)).build();
    }

    @POST
    @Path("/response")
    public Response createResponseConstraint(ConstraintRequest request) {
        constraintService.createResponseActivationQuery(request.name, request.activationEvent);
        constraintService.createResponseTargetQuery(request.name, request.targetEvent);

        constraintService.createResponseTempViolationQuery(request.name);
        constraintService.createResponseFulfillmentQuery(request.name);
        // Welche Events brauchen wir?
        // 1. Activation detecten und nach constraint status
        // 2. Target detecten und nach constraint status

        // 3. Temporary Violation: Act
        // 4. Fulfilled: Act->Tar

        return Response.created(URI.create(request.name)).build();
    }

    @POST
    @Path("/precedence")
    public Response createPrecedenceConstraint(ConstraintRequest request) {
        constraintService.createPrecedenceActivationQuery(request.name, request.activationEvent);
        constraintService.createPrecedenceTargetQuery(request.name, request.targetEvent);

        constraintService.createPrecedenceTempViolationQuery(request.name);
        constraintService.createPrecedenceFulfillmentQuery(request.name);
        constraintService.createPrecedencePermanentViolationQuery(request.name);
        // Welche Events brauchen wir?
        // 1. Activation detecten und nach constraint status
        // 2. Target detecten und nach constraint status

        // 3. Temporary Violation: Act
        // 4. Fulfillment: Act->Tar
        // 5. Permanent Violation: !(Act->Tar) in 10 ms timer interval

        return Response.created(URI.create(request.name)).build();
    }

    @POST
    @Path("/respondedExistence")
    public Response createRespondedExistenceConstraint(ConstraintRequest request) {
        constraintService.createRespondedExistenceActivationQuery(request.name, request.activationEvent);
        constraintService.createRespondedExistenceTargetQuery(request.name, request.targetEvent);


        constraintService.createRespondedExistenceForwardTempViolationQuery(request.name);
        constraintService.createRespondedExistenceBackwardTempViolationQuery(request.name);

        constraintService.createRespondedExistenceForwardFulfillmentQuery(request.name);
        constraintService.createRespondedExistenceBackwardFulfillmentQuery(request.name);
        // Welche Events brauchen wir?
        // 1. Activation detecten und nach constraint status
        // 2. Target detecten und nach constraint status

        // 3. Fulfillment: Act->Tar
        // 4. Fulfillment: Tar->Activation

        return Response.created(URI.create(request.name)).build();
    }

    @POST
    @Path("/alternateResponse")
    public Response createAlternateResponseConstraint(ConstraintRequest request) {
        constraintService.createAlternateResponseActivationQuery(request.name, request.activationEvent);
        constraintService.createAlternateResponseTargetQuery(request.name, request.targetEvent);


        constraintService.createAlternateResponseTempViolationQuery(request.name);
        constraintService.createAlternateResponseFulfillmentQuery(request.name);
        constraintService.createAlternateResponsePermanentViolationQuery(request.name);
        // Welche Events brauchen wir?

        // 1. Activation detecten und nach constraint status
        // 2. Target detecten und nach constraint status

        // 3. TempViolation: Act
        // 3. Fulfillment: Act -> Tar
        // 4. Permanent Violation: Act -> Act -> Tar

        return Response.created(URI.create(request.name)).build();
    }

    @Data
    public static class ConstraintRequest {
        private String name;
        private String activationEvent;
        private String targetEvent;
    }
}
