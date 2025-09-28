package de.ur.service.constraint;

import de.ur.dao.ConstraintType;
import de.ur.dao.CorrelationCondition;
import de.ur.dao.StatementType;
import de.ur.dto.ConditionRequest;

import java.util.Set;

public interface ConstraintHandler {
    ConstraintType getType();

    void createDetectionQuery(StatementType type, String name, String event, ConditionRequest condition, CorrelationCondition correlationCondition, Set<String> relevantKeys);

    void createFulfillmentQuery(String name, CorrelationCondition correlation);

    void createTemporaryViolationQuery(String name, CorrelationCondition correlation);

    default void createPermanentViolationQuery(String name, CorrelationCondition correlation) {
    }
}
