package de.ur.bdd;

import de.ur.dao.ConstraintStatus;
import de.ur.dao.ConstraintType;
import de.ur.dto.ConditionRequest;
import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericConstraintSteps {

    @Inject
    ConstraintService constraintService;

    @Inject
    EsperService esperService;

    @Before
    public void setup() {
        System.out.println("DEBUG: Resetting Esper and Constraints for Scenario...");
        esperService.reset();
        constraintService.resetConstraints();
        System.out.println("DEBUG: Reset Complete.");
    }

    private Map<String, String> constraintNames = new ConcurrentHashMap<>();

    // UNARY: EXISTENCE
    @Given("an {string} constraint named {string} checking for {string}")
    public void setup_unary_constraint(String typeStr, String name, String target) {
        setup_unary(typeStr, name, target, ConstraintStatus.TEMPORARY_VIOLATION);
    }

    // UNARY: NOT EXISTENCE (Starts 'INIT' technically, until violation)
    @Given("a {string} constraint named {string} ensuring {string} never occurs")
    public void setup_not_existence(String typeStr, String name, String target) {
        setup_unary(typeStr, name, target, ConstraintStatus.INIT);
    }

    private void setup_unary(String typeStr, String name, String target, ConstraintStatus initStatus) {
        System.out.println("DEBUG: Setup " + typeStr + " constraint " + name + " (Target=" + target + ")");
        ConstraintType type = ConstraintType.valueOf(typeStr.toUpperCase().replace(" ", "_"));
        constraintService.setupConstraint(
                type, name, null, null, new ConditionRequest("", "", "", null),
                target, new ConditionRequest("", "", "", null), null, initStatus, "task", "task");
        constraintNames.put(name, name);
    }

    // --- BINARY CONSTRAINTS ---

    // 1. FOLLOWED BY (Response families)
    @Given("a {string} constraint named {string} defining {string} must be eventually followed by {string}")
    public void setup_eventually_followed_by(String typeStr, String name, String activation, String target) {
        setup_binary_constraint(typeStr, name, activation, target, null);
    }

    @Given("a {string} constraint named {string} defining {string} must be immediately followed by {string}")
    public void setup_immediately_followed_by(String typeStr, String name, String activation, String target) {
        setup_binary_constraint(typeStr, name, activation, target, null);
    }

    @Given("a {string} constraint named {string} defining {string} must be alternately followed by {string}")
    public void setup_alternately_followed_by(String typeStr, String name, String activation, String target) {
        setup_binary_constraint(typeStr, name, activation, target, null);
    }

    @Given("a {string} constraint named {string} defining {string} must not be followed by {string}")
    public void setup_not_followed_by(String typeStr, String name, String activation, String target) {
        // Not Response REQUIRES a timer for its EPL window logic
        setup_binary_constraint(typeStr, name, activation, target, 5L);
    }

    // 2. PRECEDED BY (Precedence families)
    @Given("a {string} constraint named {string} defining {string} must be preceded by {string}")
    public void setup_preceded_by(String typeStr, String name, String target, String activation) {
        setup_binary_constraint(typeStr, name, activation, target, null);
    }

    @Given("a {string} constraint named {string} defining {string} must be immediately preceded by {string}")
    public void setup_immediately_preceded_by(String typeStr, String name, String target, String activation) {
        setup_binary_constraint(typeStr, name, activation, target, null);
    }

    @Given("a {string} constraint named {string} defining {string} must be alternately preceded by {string}")
    public void setup_alternately_preceded_by(String typeStr, String name, String target, String activation) {
        setup_binary_constraint(typeStr, name, activation, target, null);
    }

    @Given("a {string} constraint named {string} defining {string} must not be preceded by {string}")
    public void setup_not_preceded_by(String typeStr, String name, String target, String activation) {
        // Providing timer just in case logic uses one, usually Not Precedence is
        // check-on-occurrence
        setup_binary_constraint(typeStr, name, activation, target, 5L);
    }

    // 3. RESPONDED EXISTENCE
    @Given("a {string} constraint named {string} defining {string} must be responded to by {string}")
    public void setup_responded_existence(String typeStr, String name, String activation, String target) {
        setup_binary_constraint(typeStr, name, activation, target, null);
    }

    private void setup_binary_constraint(String typeStr, String name, String activation, String target, Long timer) {
        System.out.println("DEBUG: Setup " + typeStr + " constraint " + name +
                " (A=" + activation + ", B=" + target + ", Timer=" + timer + ")");
        ConstraintType type = ConstraintType.valueOf(typeStr.toUpperCase().replace(" ", "_"));

        constraintService.setupConstraint(
                type,
                name,
                timer,
                activation,
                new ConditionRequest("", "", "", null),
                target,
                new ConditionRequest("", "", "", null),
                null,
                ConstraintStatus.INIT,
                "task", "task");
        constraintNames.put(name, name);
    }

    @Given("the constraint {string} is in {string} state")
    public void constraint_is_in_state(String name, String statusStr) {
        the_status_should_be(name, statusStr);
    }

    @When("event {string} occurs")
    public void event_occurs(String eventName) {
        System.out.println("DEBUG: Sending event " + eventName);
        de.ur.dao.GenericEvent event = new de.ur.dao.GenericEvent(
                java.util.UUID.randomUUID().toString(),
                eventName,
                System.currentTimeMillis(),
                new java.util.HashMap<>());
        esperService.sendEvent(event);
    }

    @When("{int} seconds pass")
    public void time_passes(int seconds) {
        System.out.println("DEBUG: Waiting " + seconds + " seconds...");
        org.awaitility.Awaitility.await()
                .pollDelay(seconds, java.util.concurrent.TimeUnit.SECONDS)
                .until(() -> true);
    }

    @Then("the status of {string} should be {string}")
    public void the_status_should_be(String name, String expectedStatusStr) {
        ConstraintStatus expected = ConstraintStatus.valueOf(expectedStatusStr);
        System.out.println("DEBUG: Awaiting status for " + name + " to be " + expected);

        org.awaitility.Awaitility.await()
                .atMost(1, java.util.concurrent.TimeUnit.SECONDS)
                .pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConstraintStatus actual = constraintService.getConstraints().get(name).getStatus();
                    System.out.println("DEBUG: Check status for " + name + ": Actual=" + actual);
                    Assertions.assertEquals(expected, actual, "Constraint status mismatch for " + name);
                });
    }
}
