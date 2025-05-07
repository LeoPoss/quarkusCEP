package de.ur.dao;

import lombok.*;

import java.util.HashMap;

@Getter
@Setter
@NoArgsConstructor
public class Constraint {
    private String name;
    private HashMap<String, String> eplStatements;
    private ConstraintType type;
    private ConstraintStatus status;

    public Constraint(String name, String eplName, String eplStatement, ConstraintType type, ConstraintStatus status) {
        this.name = name;
        this.eplStatements = new HashMap<>();
        eplStatements.put(eplName, eplStatement);
        this.type = type;
        this.status = status;
    }
}
