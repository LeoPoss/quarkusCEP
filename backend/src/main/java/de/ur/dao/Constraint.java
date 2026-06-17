package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;

@Data
@AllArgsConstructor
public class Constraint {
    private String name;
    private Long withinPeriod;
    private ArrayList<EplStatement> eplStatements;
    private Event activationEvent;
    private ConstraintCondition activationCondition;
    private Event targetEvent;
    private ConstraintCondition targetCondition;
    private CorrelationCondition correlationCondition;
    private ConstraintType type;
    private ConstraintStatus status;
    private boolean autoExecute;
    private String autoExecutePayload;

    public void updateStatus(ConstraintStatus status) {
        if (this.status == ConstraintStatus.PERMANENT_VIOLATION) {
            return;
        }
        // Templates like EXISTENCE, RESPONDED_EXISTENCE are satisfied permanently once fulfilled.
        // Recurring templates (RESPONSE, etc.) create a new obligation on each activation.
        if (this.status == ConstraintStatus.FULFILLED && this.type != null && this.type.isFulfilledPermanent()) {
            return;
        }
        this.status = status;
    }
}
