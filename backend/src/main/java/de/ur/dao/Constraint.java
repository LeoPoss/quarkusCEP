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
        // Never downgrade from FULFILLED
        if (this.status == ConstraintStatus.FULFILLED) {
            return;
        }
        this.status = status;
    }
}
