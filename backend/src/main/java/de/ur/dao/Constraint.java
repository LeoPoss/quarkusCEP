package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;

@Data
@AllArgsConstructor
public class Constraint {
    private String name;
    private ArrayList<EplStatement> eplStatements;
    private String activationEvent;
    private ConstraintCondition activationCondition;
    private String targetEvent;
    private ConstraintCondition targetCondition;
    private CorrelationCondition correlationCondition;
    private ConstraintType type;
    private ConstraintStatus status;

    public void updateStatus(ConstraintStatus status) {
        if (this.status == ConstraintStatus.PERMANENT_VIOLATION) {
            return;
        }
        this.status = status;
    }

    public Constraint(Constraint original) {
        this.name = original.name;
        this.activationEvent = original.activationEvent;
        this.targetEvent = original.targetEvent;
        this.type = original.type;
        this.status = original.status;

        this.eplStatements = (original.eplStatements != null)
                ? new ArrayList<>(original.eplStatements)
                : null;

        this.activationCondition = original.activationCondition;
        this.targetCondition = original.targetCondition;
        this.correlationCondition = original.correlationCondition;
    }

    public void updateStatus(ConstraintStatus status, ConstraintType type) {
        switch (type) {
            case ALTERNATE_PRECEDENCE:
                this.status = status;
        }
    }
}
