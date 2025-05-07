package de.ur.service;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.common.client.util.NameAccessModifier;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompiler;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;
import de.ur.dao.SampleEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.BadRequestException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class EsperService {
    @Getter
    private EPRuntime runtime;
    private final Map<String, EPDeployment> deployments = new ConcurrentHashMap<>();
    private final EPCompiler compiler = EPCompilerProvider.getCompiler();

    void onStart(@Observes StartupEvent event) {
        log.info("Initializing Esper service");
        Configuration configuration = new Configuration();

        configureEventTypes(configuration);

        runtime = EPRuntimeProvider.getDefaultRuntime(configuration);
        log.info("Esper runtime initialized");


        CompilerArguments compilerArgs = new CompilerArguments();
        compilerArgs.getPath().add(runtime.getRuntimePath());

        try {
            //deployStatements("ConstraintStatusTableDefinition", "CREATE SCHEMA constraintStatus AS (id String, name String, type String);");
            log.info("ConstraintStatus table defined.");
        } catch (BadRequestException e) {
            log.error("Failed to define ConstraintStatus table on startup", e);
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        log.info("Destroying Esper runtime");
        if (runtime != null) {
            runtime.destroy();
        }
    }

    private void configureEventTypes(Configuration configuration) {
        configuration.getCommon().addEventType(SampleEvent.class);
    }


    public EPStatement deployStatements(String name, String query) {
        try {
            CompilerArguments args = new CompilerArguments();
            args.getPath().add(runtime.getRuntimePath());

            args.getOptions()
                    .setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);
            EPCompiled compiled;
            if (name != null) {
                compiled = compiler.compile("@name('" + name + "') " + query, args);
            } else {
                compiled = compiler.compile(query, args);
            }
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


    public void sendEvent(Map<String, Object> event, String eventTypeName) {
        if (runtime != null) {
            runtime.getEventService().sendEventMap(event, eventTypeName);
        }
    }

    public boolean undeploy(String deploymentId) {
        try {
            runtime.getDeploymentService().undeploy(deploymentId);
            deployments.remove(deploymentId);
            return true;
        } catch (EPUndeployException e) {
            log.error("Failed to undeploy: {}", deploymentId, e);
            return false;
        }
    }

}