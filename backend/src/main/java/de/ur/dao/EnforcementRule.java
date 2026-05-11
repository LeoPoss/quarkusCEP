package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EnforcementRule {
    private String name;
    private String signalType;
    private String conditionParam;
    private String conditionOperator;
    private String conditionValue;
    private long durationSeconds;
    private String actionEventType;
    private boolean active;
    private Long lastFiredAt;
}
