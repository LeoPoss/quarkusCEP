package de.ur.service;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.common.client.util.NameAccessModifier;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompiler;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;
import de.ur.dao.ConstraintType;
import de.ur.dao.GenericEvent;
import de.ur.dao.StatementType;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class EsperService {
    private final ConstraintService constraintService;
    @Getter
    private EPRuntime runtime;
    private final Map<String, EPDeployment> deployments = new ConcurrentHashMap<>();
    private final EPCompiler compiler = EPCompilerProvider.getCompiler();

    @Inject
    public EsperService(ConstraintService constraintService) {
        this.constraintService = constraintService;
    }

    void onStart(@Observes StartupEvent event) {
        log.info("Initializing Esper service");
        Configuration configuration = new Configuration();

        configureEventTypes(configuration);

        runtime = EPRuntimeProvider.getDefaultRuntime(configuration);
        log.info("Esper runtime initialized");

        // Deploy the constraintStatus schema so it exists before any constraints are
        // created
        deployConstraintStatusSchema();

        CompilerArguments compilerArgs = new CompilerArguments();
        compilerArgs.getPath().add(runtime.getRuntimePath());
    }

    void onStop(@Observes ShutdownEvent event) {
        log.info("Destroying Esper runtime");
        if (runtime != null) {
            runtime.destroy();
        }
    }

    private void configureEventTypes(Configuration configuration) {
        configuration.getCommon().addEventType(GenericEvent.class);
        configuration.getCommon().addEventType(de.ur.dao.TestStartEvent.class);
        configuration.getCommon().addEventType(de.ur.dao.TestEndEvent.class);
        configuration.getCommon().addEventType(de.ur.dao.TestViolationEvent.class);
    }

    /**
     * Deploy the constraintStatus schema so that SELECT queries can reference it.
     * This creates the stream type before any constraints try to use it.
     */
    private void deployConstraintStatusSchema() {
        try {
            String schemaQuery = """
                        @public @buseventtype
                        create schema constraintStatus(
                            id string,
                            name string,
                            type string,
                            timestamp long,
                            payload java.util.Map
                        )
                    """;
            CompilerArguments args = new CompilerArguments();
            args.getPath().add(runtime.getRuntimePath());
            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);
            EPCompiled compiled = compiler.compile(schemaQuery, args);
            runtime.getDeploymentService().deploy(compiled);
            log.info("Deployed constraintStatus schema");
        } catch (Exception e) {
            log.error("Failed to deploy constraintStatus schema", e);
        }
    }

    /**
     * Deploy the test session context for hypothetical event analysis.
     * This context is initiated by TestStartEvent and terminated by TestEndEvent.
     */
    public void deployTestContext() {
        try {
            // Create the test session context
            String contextQuery = """
                    @public
                    create context TestSessionContext
                        initiated by TestStartEvent as startEvent
                        terminated by TestEndEvent(testId = startEvent.testId)
                    """;

            CompilerArguments args = new CompilerArguments();
            args.getPath().add(runtime.getRuntimePath());
            args.getOptions().setAccessModifierContext(env -> NameAccessModifier.PUBLIC);
            EPCompiled compiled = compiler.compile(contextQuery, args);
            runtime.getDeploymentService().deploy(compiled);
            log.info("Deployed TestSessionContext");
        } catch (Exception e) {
            log.error("Failed to deploy TestSessionContext", e);
        }
    }

    /**
     * Deploy an analysis pattern for a specific constraint type within the test
     * context.
     * These patterns fire when a violation would occur.
     * 
     * @param constraintName  Name of the constraint
     * @param constraintType  Type of the constraint
     * @param activationEvent Activation event name
     * @param targetEvent     Target event name
     * @param onViolation     Callback when violation is detected (receives testId,
     *                        constraintName)
     */
    public void deployAnalysisPattern(String constraintName, ConstraintType constraintType,
            String activationEvent, String targetEvent,
            java.util.function.BiConsumer<String, String> onViolation) {
        try {
            String patternQuery = buildAnalysisPatternQuery(constraintName, constraintType, activationEvent,
                    targetEvent);
            if (patternQuery == null) {
                log.debug("No analysis pattern needed for constraint type: {}", constraintType);
                return;
            }

            CompilerArguments args = new CompilerArguments();
            args.getPath().add(runtime.getRuntimePath());
            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);
            EPCompiled compiled = compiler.compile(patternQuery, args);
            var deployment = runtime.getDeploymentService().deploy(compiled);

            // Add listener to track violations
            if (deployment.getStatements().length > 0 && onViolation != null) {
                deployment.getStatements()[0].addListener((newEvents, oldEvents, statement, rt) -> {
                    if (newEvents != null) {
                        for (var event : newEvents) {
                            String testId = (String) event.get("testId");
                            String cName = (String) event.get("constraintName");
                            log.debug("Analysis pattern fired: testId={}, constraint={}", testId, cName);
                            onViolation.accept(testId, cName);
                        }
                    }
                });
            }

            log.info("Deployed analysis pattern for constraint: {}", constraintName);
        } catch (Exception e) {
            log.error("Failed to deploy analysis pattern for {}: {}", constraintName, e.getMessage());
        }
    }

    private String buildAnalysisPatternQuery(String constraintName, ConstraintType constraintType,
            String activationEvent, String targetEvent) {
        // Build EPL pattern that checks within TestSessionContext if violation would
        // occur
        return switch (constraintType) {
            case NOT_EXISTENCE ->
                """
                        @name('%s_analysis')
                        context TestSessionContext
                        select startEvent.testId as testId, '%s' as constraintName, '%s' as constraintType, startEvent.hypotheticalEvent as hypotheticalEvent
                        from TestStartEvent as startEvent
                        where startEvent.constraintName = '%s'
                          and startEvent.hypotheticalEvent = '%s'
                        """
                        .formatted(constraintName, constraintName, constraintType, constraintName, targetEvent);

            case PRECEDENCE, ALTERNATE_PRECEDENCE ->
                """
                        @name('%s_analysis')
                        context TestSessionContext
                        select startEvent.testId as testId, '%s' as constraintName, '%s' as constraintType, startEvent.hypotheticalEvent as hypotheticalEvent
                        from TestStartEvent as startEvent
                        where startEvent.constraintName = '%s'
                          and startEvent.hypotheticalEvent = '%s'
                          and startEvent.hasActivation = false
                        """
                        .formatted(constraintName, constraintName, constraintType, constraintName, targetEvent);

            case CHAIN_PRECEDENCE ->
                """
                        @name('%s_analysis')
                        context TestSessionContext
                        select startEvent.testId as testId, '%s' as constraintName, '%s' as constraintType, startEvent.hypotheticalEvent as hypotheticalEvent
                        from TestStartEvent as startEvent
                        where startEvent.constraintName = '%s'
                          and startEvent.hypotheticalEvent = '%s'
                          and startEvent.lastEventType != '%s'
                        """
                        .formatted(constraintName, constraintName, constraintType, constraintName, targetEvent,
                                activationEvent);

            case CHAIN_RESPONSE ->
                """
                        @name('%s_analysis')
                        context TestSessionContext
                        select startEvent.testId as testId, '%s' as constraintName, '%s' as constraintType, startEvent.hypotheticalEvent as hypotheticalEvent
                        from TestStartEvent as startEvent
                        where startEvent.constraintName = '%s'
                          and startEvent.currentStatus = 'TEMPORARY_VIOLATION'
                          and startEvent.hypotheticalEvent != '%s'
                        """
                        .formatted(constraintName, constraintName, constraintType, constraintName, targetEvent);

            case ALTERNATE_RESPONSE ->
                """
                        @name('%s_analysis')
                        context TestSessionContext
                        select startEvent.testId as testId, '%s' as constraintName, '%s' as constraintType, startEvent.hypotheticalEvent as hypotheticalEvent
                        from TestStartEvent as startEvent
                        where startEvent.constraintName = '%s'
                          and startEvent.currentStatus = 'TEMPORARY_VIOLATION'
                          and startEvent.hypotheticalEvent = '%s'
                        """
                        .formatted(constraintName, constraintName, constraintType, constraintName, activationEvent);

            case NOT_RESPONSE ->
                """
                        @name('%s_analysis')
                        context TestSessionContext
                        select startEvent.testId as testId, '%s' as constraintName, '%s' as constraintType, startEvent.hypotheticalEvent as hypotheticalEvent
                        from TestStartEvent as startEvent
                        where startEvent.constraintName = '%s'
                          and (
                            (startEvent.currentStatus = 'TEMPORARY_VIOLATION' and startEvent.hypotheticalEvent = '%s')
                            or (startEvent.currentStatus = 'INIT' and startEvent.hypotheticalEvent = '%s' and startEvent.hasActivation = true)
                          )
                        """
                        .formatted(constraintName, constraintName, constraintType, constraintName, targetEvent,
                                targetEvent);

            case NOT_PRECEDENCE ->
                """
                        @name('%s_analysis')
                        context TestSessionContext
                        select startEvent.testId as testId, '%s' as constraintName, '%s' as constraintType, startEvent.hypotheticalEvent as hypotheticalEvent
                        from TestStartEvent as startEvent
                        where startEvent.constraintName = '%s'
                          and startEvent.hypotheticalEvent = '%s'
                          and (startEvent.hasActivation = true or startEvent.currentStatus = 'TEMPORARY_VIOLATION')
                        """
                        .formatted(constraintName, constraintName, constraintType, constraintName, targetEvent);

            // RESPONSE, RESPONDED_EXISTENCE, EXISTENCE: no immediate violations
            default -> null;
        };
    }

    public EPStatement deployStatements(String name, String query) {
        try {
            CompilerArguments args = new CompilerArguments();
            args.getPath().add(runtime.getRuntimePath());

            args.getOptions()
                    .setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);
            EPCompiled compiled = compiler.compile("@name('" + name + "') " + query, args);

            EPDeployment deployment = runtime.getDeploymentService()
                    .deploy(compiled);
            return deployment.getStatements()[0];
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean addListener(String deploymentId, String statementName, UpdateListener listener) {
        EPDeployment deployment = deployments.get(deploymentId);
        if (deployment == null) {
            log.warn("Deployment not found: {}", deploymentId);
            return false;
        }

        EPStatement statement = runtime.getDeploymentService().getStatement(deploymentId, statementName);
        if (statement == null) {
            log.warn("Statement not found: {} in deployment {}", statementName, deploymentId);
            return false;
        }

        statement.addListener(listener);
        return true;
    }

    public boolean removeListener(String deploymentId, String statementName, UpdateListener listener) {
        EPDeployment deployment = deployments.get(deploymentId);
        if (deployment == null) {
            return false;
        }

        EPStatement statement = runtime.getDeploymentService().getStatement(deploymentId, statementName);
        if (statement == null) {
            return false;
        }

        statement.removeListener(listener);
        return true;
    }

    public void sendEvent(Object event) {
        if (runtime != null) {
            runtime.getEventService().sendEventBean(event, event.getClass().getSimpleName());
        }
    }

    public void undeploy(String deploymentId) {
        try {
            runtime.getDeploymentService().undeploy(deploymentId);
        } catch (EPUndeployException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeConstraint(String name) {
        var constraint = constraintService.getConstraints().get(name);
        var deploymentService = runtime.getDeploymentService();

        // TODO Check what can be removed and what can't

        constraint.getEplStatements().forEach((s) -> {
            if (List.of(StatementType.FULFILLMENT, StatementType.PERMANENT_VIOLATION).contains(s.type())) {
                try {
                    deploymentService.undeploy(s.deploymentId());
                } catch (EPUndeployException e) {
                    // Ignore if deployment not found, it might have been removed already
                    if (e instanceof EPUndeployNotFoundException) {
                        log.debug("Deployment {} not found during removal, ignoring.", s.deploymentId());
                    } else {
                        log.warn("Failed to undeploy statement {}: {}", s.deploymentId(), e.getMessage());
                    }
                }
            }
        });
    }

    public void reset() {
        if (runtime != null) {
            runtime.destroy();
        }

        Configuration configuration = new Configuration();
        configureEventTypes(configuration);
        runtime = EPRuntimeProvider.getDefaultRuntime(configuration);

        // Re-deploy the constraintStatus schema after reset
        deployConstraintStatusSchema();

        log.info("Esper runtime has been reset and reinitialized");
    }
}
