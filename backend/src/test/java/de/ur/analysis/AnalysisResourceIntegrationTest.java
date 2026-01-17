package de.ur.analysis;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for Analysis REST endpoints.
 * Tests the full HTTP flow for allowed-tasks and finishability endpoints.
 */
@QuarkusTest
class AnalysisResourceIntegrationTest {

        @BeforeEach
        void resetEsper() {
                // Reset Esper before each test
                given()
                                .contentType(ContentType.JSON)
                                .when().post("/esper/reset")
                                .then().statusCode(200);
        }

        // ==================== NOT_EXISTENCE Tests ====================

        @Nested
        @DisplayName("NOT_EXISTENCE Constraint")
        class NotExistenceTests {

                @Test
                @DisplayName("Target event should be marked unsafe")
                void targetEventShouldBeUnsafe() {
                        // Create NOT_EXISTENCE constraint
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {
                                                            "name": "NoForbiddenTask",
                                                            "targetEvent": "ForbiddenTask",
                                                            "targetEventType": "task"
                                                        }
                                                        """)
                                        .when().post("/constraints/notexistence")
                                        .then().statusCode(201);

                        // Check allowed tasks
                        given()
                                        .when().get("/analysis/allowed-tasks")
                                        .then()
                                        .statusCode(200)
                                        .body("size()", is(1))
                                        .body("[0].task", is("ForbiddenTask"))
                                        .body("[0].isUnsafe", is(true));
                }
        }

        // ==================== PRECEDENCE Tests ====================

        @Nested
        @DisplayName("PRECEDENCE Constraint")
        class PrecedenceTests {

                @Test
                @DisplayName("Target unsafe before activation, safe after")
                void targetStatusChangesWithActivation() {
                        // Create PRECEDENCE(Login, Purchase)
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {
                                                            "name": "MustLoginFirst",
                                                            "activationEvent": "Login",
                                                            "targetEvent": "Purchase",
                                                            "activationEventType": "task",
                                                            "targetEventType": "task"
                                                        }
                                                        """)
                                        .when().post("/constraints/precedence")
                                        .then().statusCode(201);

                        // Before Login - Purchase should be unsafe
                        given()
                                        .when().get("/analysis/allowed-tasks")
                                        .then()
                                        .statusCode(200)
                                        .body("find { it.task == 'Purchase' }.isUnsafe", is(true))
                                        .body("find { it.task == 'Login' }.isUnsafe", is(false));

                        // Send Login event
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {"eventType": "Login"}
                                                        """)
                                        .when().post("/esper/event")
                                        .then().statusCode(200);

                        // After Login - Purchase should be safe
                        given()
                                        .when().get("/analysis/allowed-tasks")
                                        .then()
                                        .statusCode(200)
                                        .body("find { it.task == 'Purchase' }.isUnsafe", is(false))
                                        .body("find { it.task == 'Login' }.isUnsafe", is(false));
                }
        }

        // ==================== RESPONSE Tests ====================

        @Nested
        @DisplayName("RESPONSE Constraint")
        class ResponseTests {

                @Test
                @DisplayName("Both events should be safe (no immediate violations)")
                void bothEventsSafe() {
                        // Create RESPONSE(A, B)
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {
                                                            "name": "ResponseAB",
                                                            "activationEvent": "A",
                                                            "targetEvent": "B",
                                                            "activationEventType": "task",
                                                            "targetEventType": "task"
                                                        }
                                                        """)
                                        .when().post("/constraints/response")
                                        .then().statusCode(201);

                        // Both events should be safe (RESPONSE has no immediate violations)
                        given()
                                        .when().get("/analysis/allowed-tasks")
                                        .then()
                                        .statusCode(200)
                                        .body("find { it.task == 'A' }.isUnsafe", is(false))
                                        .body("find { it.task == 'B' }.isUnsafe", is(false));
                }
        }

        // ==================== Finishability Tests ====================

        @Nested
        @DisplayName("Finishability Endpoint")
        class FinishabilityTests {

                @Test
                @DisplayName("Finishable with no constraints")
                void finishableWithNoConstraints() {
                        given()
                                        .when().get("/analysis/finishability")
                                        .then()
                                        .statusCode(200)
                                        .body("canFinish", is(true))
                                        .body("reasons", empty());
                }

                @Test
                @DisplayName("Not finishable when RESPONSE is temporarily violated")
                void notFinishableWithTemporaryViolation() {
                        // Create RESPONSE(A, B)
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {
                                                            "name": "ResponseAB",
                                                            "activationEvent": "A",
                                                            "targetEvent": "B",
                                                            "activationEventType": "task",
                                                            "targetEventType": "task"
                                                        }
                                                        """)
                                        .when().post("/constraints/response")
                                        .then().statusCode(201);

                        // Send A - constraint becomes TEMPORARILY_VIOLATED (waiting for B)
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {"eventType": "A"}
                                                        """)
                                        .when().post("/esper/event")
                                        .then().statusCode(200);

                        // Should not be finishable
                        given()
                                        .when().get("/analysis/finishability")
                                        .then()
                                        .statusCode(200)
                                        .body("canFinish", is(false))
                                        .body("reasons", not(empty()));
                }

                @Test
                @DisplayName("Finishable when constraint is fulfilled")
                void finishableWhenFulfilled() {
                        // Create RESPONSE(A, B)
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {
                                                            "name": "ResponseAB",
                                                            "activationEvent": "A",
                                                            "targetEvent": "B",
                                                            "activationEventType": "task",
                                                            "targetEventType": "task"
                                                        }
                                                        """)
                                        .when().post("/constraints/response")
                                        .then().statusCode(201);

                        // Send A then B - constraint fulfilled
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {"eventType": "A"}
                                                        """)
                                        .when().post("/esper/event")
                                        .then().statusCode(200);

                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {"eventType": "B"}
                                                        """)
                                        .when().post("/esper/event")
                                        .then().statusCode(200);

                        // Should be finishable now
                        given()
                                        .when().get("/analysis/finishability")
                                        .then()
                                        .statusCode(200)
                                        .body("canFinish", is(true));
                }
        }

        // ==================== Multiple Constraints Tests ====================

        @Nested
        @DisplayName("Multiple Constraints")
        class MultipleConstraintsTests {

                @Test
                @DisplayName("Event unsafe if it violates ANY constraint")
                void eventUnsafeIfAnyConstraintViolated() {
                        // Create two constraints
                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {
                                                            "name": "NoDeleteWithoutBackup",
                                                            "targetEvent": "Delete",
                                                            "targetEventType": "task"
                                                        }
                                                        """)
                                        .when().post("/constraints/notexistence")
                                        .then().statusCode(201);

                        given()
                                        .contentType(ContentType.JSON)
                                        .body("""
                                                        {
                                                            "name": "MustSaveFirst",
                                                            "activationEvent": "Save",
                                                            "targetEvent": "Delete",
                                                            "activationEventType": "task",
                                                            "targetEventType": "task"
                                                        }
                                                        """)
                                        .when().post("/constraints/precedence")
                                        .then().statusCode(201);

                        // Delete should be unsafe (violates NOT_EXISTENCE)
                        given()
                                        .when().get("/analysis/allowed-tasks")
                                        .then()
                                        .statusCode(200)
                                        .body("find { it.task == 'Delete' }.isUnsafe", is(true))
                                        .body("find { it.task == 'Save' }.isUnsafe", is(false));
                }
        }
}
