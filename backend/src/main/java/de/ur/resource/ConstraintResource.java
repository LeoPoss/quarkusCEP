package de.ur.resource;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Path("/constraints")
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
        return Response.ok(constraintService.getConstraints().values()).build();
    }

    // Prozessstart und Ende -> Muss auch in Paper, Prozess kann nicht beendet werden, solange nicht alle constraints erfüllt

    // INSERT INTO ist quasi reaction auf FROM PATTERN
    // SELECT triggert dann listener der code ausführt, deswegen zwei Ebenen

    // TODO: Wann können wir löschen, also welche status sind final und welche nicht
    // TODO: Evtl. nicht constraints löschen sondern alte activation und target

    // "targetCondition": "cast(payload('val'), double)>300"

    @POST
    @Path("/existence")
    public Response createExistenceConstraint(ConstraintRequest request) {
        constraintService.addConstraint(request.name, ConstraintType.EXISTENCE, request.activationEvent, request.activationCondition, request.targetEvent, request.targetCondition, ConstraintStatus.TEMPORARY_VIOLATION);

        constraintService.createExistenceActivationQuery(request.name, request.targetEvent, request.targetCondition);

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
        constraintService.addConstraint(request.name, ConstraintType.RESPONSE, request.activationEvent, request.activationCondition, request.targetEvent, request.targetCondition, ConstraintStatus.INIT);

        constraintService.createResponseActivationQuery(request.name, request.activationEvent, request.activationCondition);
        constraintService.createResponseTargetQuery(request.name, request.targetEvent, request.targetCondition);

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
    @Path("/respondedexistence")
    public Response createRespondedExistenceConstraint(ConstraintRequest request) {
        constraintService.addConstraint(request.name, ConstraintType.RESPONDEDEXISTENCE, request.activationEvent, request.activationCondition, request.targetEvent, request.targetCondition, ConstraintStatus.INIT);

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
    @Path("/alternateresponse")
    public Response createAlternateResponseConstraint(ConstraintRequest request) {
        constraintService.addConstraint(request.name, ConstraintType.ALTERNATERESPONSE, request.activationEvent, request.activationCondition, request.targetEvent, request.targetCondition, ConstraintStatus.INIT);

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

    @POST
    @Path("/chainresponse")
    public Response createChainResponseConstraint(ConstraintRequest request) {
        constraintService.addConstraint(request.name, ConstraintType.CHAINRESPONSE, request.activationEvent, request.activationCondition, request.targetEvent, request.targetCondition, ConstraintStatus.INIT);

        constraintService.createChainResponseActivationQuery(request.name, request.activationEvent);
        constraintService.createChainResponseTargetQuery(request.name, request.targetEvent);

        constraintService.createChainResponseTempViolationQuery(request.name);
        constraintService.createChainResponseFulfillmentQuery(request.name);
        constraintService.createChainResponsePermanentViolationQuery(request.name);

        // Welche Events brauchen wir?

        // 1. Activation detecten und nach constraint status
        // 2. Target detecten und nach constraint status

        // 3. TempViolation: Act
        // 3. Fulfillment: Act -> Tar
        // 4. Permanent Violation: Act -> X -> Tar

        return Response.created(URI.create(request.name)).build();
    }

    @POST
    @Path("/precedence")
    public Response createPrecedenceConstraint(ConstraintRequest request) {
        constraintService.addConstraint(request.name, ConstraintType.PRECEDENCE, request.activationEvent, request.activationCondition, request.targetEvent, request.targetCondition, ConstraintStatus.INIT);

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
    @Path("notresponse")
    public Response createNotResponseConstraint(ConstraintRequest request) {
        constraintService.addConstraint(request.name, ConstraintType.NOTRESPONSE, request.activationEvent, request.activationCondition, request.targetEvent, request.targetCondition, ConstraintStatus.FULFILLED);

        constraintService.createNotResponseActivationQuery(request.name, request.activationEvent);
        constraintService.createNotResponseTargetQuery(request.name, request.targetEvent);

        constraintService.createNotResponseTempViolationQuery(request.name);
        constraintService.createNotResponsePermanentViolationQuery(request.name);
        // Welche Events brauchen wir?

        // 1. Activation detecten und nach constraint status
        // 2. Target detecten und nach constraint status

        // 3. TempViolation: Act
        // 4. Permanent Violation: Act -> Tar
        // INIT: Fulfilled

        return Response.created(URI.create(request.name)).build();
    }

    public record ConstraintRequest(String name, String activationEvent, String targetEvent,
                                    ConditionRequest targetCondition, ConditionRequest activationCondition) {
    }

    public record ConditionRequest(String param, String operator, String value) {
        public boolean isValid() {
            return (param != null && operator != null && value != null && !param.isBlank() && !operator.isBlank() && !value.isBlank());
        }

        public String getConditionQueryPart() {
            String querySegment;

            if ("true".equalsIgnoreCase(this.value) || "false".equalsIgnoreCase(this.value)) {
                String booleanLiteral = this.value.toUpperCase();
                querySegment = " AND cast(payload('%s'), boolean) %s %s".formatted(this.param, this.operator, booleanLiteral);
            } else {
                try {
                    querySegment = " AND cast(payload('%s'), double) %s %s".formatted(this.param, this.operator, this.value);
                } catch (NumberFormatException e) {
                    String sqlSafeString = this.value.replace("'", "''");
                    querySegment = " AND payload('%s') %s '%s'".formatted(this.param, this.operator, sqlSafeString);
                }
            }
            return querySegment;
        }
    }
}
