package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;

@Data
@AllArgsConstructor
public class Constraint {
    private String name;
    private ArrayList<EplStatement> eplStatements;
    private ConstraintType type;
    private ConstraintStatus status;
}
