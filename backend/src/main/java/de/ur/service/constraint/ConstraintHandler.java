package de.ur.service.constraint;

import de.ur.dao.ConstraintType;
import de.ur.dao.StatementType;
import de.ur.dto.ConditionRequest;

public interface ConstraintHandler {
    ConstraintType getType();

    void createDetectionQuery(StatementType type, String name, String event, ConditionRequest condition);

    void createFulfillmentQuery(String name);

    void createTemporaryViolationQuery(String name);

    default void createPermanentViolationQuery(String name) {
    }
}
