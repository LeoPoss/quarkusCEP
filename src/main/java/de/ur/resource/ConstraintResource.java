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
        return Response.ok(constraintService.getConstraints()).build();
    }

    // Prozessstart und Ende -> Muss auch in Paper, Prozess kann nicht beendet werden, solange nicht alle constraints erfüllt
    // dennoch irgendwo in memory constraints mit name, query und status speichern und da jeweils start status setzen
    // INSERT INTO ist quasi reaction auf FROM PATTERN
    // SELECT triggert dann listener der code ausführt, deswegen zwei Ebenen

    @POST
    @Path("/existence")
    public Response createExistenceConstraint(ConstraintResource.ExistenceConstraintRequest request) {
        constraintService.createExistenceActivationQuery(request.name, request.targetEvent);
        //esperService.deployStatements("ConstraintStatusTableDefinition2", "@public CREATE SCHEMA constraintStatus(id string, name string, type string);");
        constraintService.createExistenceFulfillmentQuery(request.name);

        // Welche Events brauchen wir?
        // 1. Activation detecten und nach constraint status

        // 2. Activation von constraints status -> Status update

        // init -> fulfilled

        return Response.created(URI.create(request.name)).build();
    }

    @POST
    @Path("/response")
    public Response createResponseConstraint(ConstraintResource.ExistenceConstraintRequest request) {
        // Welche Events brauchen wir?
        // 1. Activation detecten und nach constraint status
        // 2. Target detecten und nach constraint status

        // 3. Temporary Violation: Act
        // 4. Fulfilled: Act->Tar

        return Response.created(URI.create(request.name)).build();
    }

    @POST
    @Path("/precedence")
    public Response createPrecedenceConstraint(ConstraintResource.ExistenceConstraintRequest request) {
        // Welche Events brauchen wir?
        // 1. Activation detecten und nach constraint status
        // 2. Target detecten und nach constraint status

        // 3. Temporary Violation: Act
        // 4. Fulfillment: Act->Tar
        // 5. Permanent Violation: !(Act->Tar) in 10 ms timer interval

        return Response.created(URI.create(request.name)).build();
    }

    @Data
    public static class ExistenceConstraintRequest {
        private String name;
        private String targetEvent;
    }
}
