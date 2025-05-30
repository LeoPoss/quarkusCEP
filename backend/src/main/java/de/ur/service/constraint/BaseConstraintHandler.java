package de.ur.service.constraint;

import de.ur.dao.StatementType;
import de.ur.dto.ConditionRequest;
import de.ur.service.ConstraintService;
import de.ur.service.EsperService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public abstract class BaseConstraintHandler implements ConstraintHandler {
    @Inject
    protected EsperService esperService;
    @Inject
    protected ConstraintService constraintService;

    @Override
    public void createDetectionQuery(StatementType type, String name, String event, ConditionRequest condition) {
        String query = """
                INSERT INTO constraintStatus
                SELECT id, '%s' as name, '%s' as type
                FROM GenericEvent WHERE type = '%s'
                """.formatted(name, type, event);

        if (condition.isValid()) {
            query += condition.getConditionQueryPart();
        }

        var statement = esperService.deployStatements(name, query);
        addConstraintStatement(name, statement.getDeploymentId(), type, query);
    }

    protected void addConstraintStatement(String name, String eplId, StatementType eplType, String eplStatement) {
        constraintService.addConstraintStatement(name, eplId, eplType, eplStatement);
    }
}
