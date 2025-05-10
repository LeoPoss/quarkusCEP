package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConstraintCondition {
    private String param;
    private String operator;
    private String value;
}
