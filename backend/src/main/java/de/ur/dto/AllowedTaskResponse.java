package de.ur.dto;

import java.util.List;
import java.util.Map;

public record AllowedTaskResponse(
    String task,
    boolean isUnsafe,
    Map<String, String> unsafeConditions,
    List<String> violatedBy
) {
    public static AllowedTaskResponse safe(String task) {
        return new AllowedTaskResponse(task, false, null, List.of());
    }
    
    public static AllowedTaskResponse unsafe(String task, Map<String, String> conditions, String violatedByConstraint) {
        return new AllowedTaskResponse(task, true, conditions, List.of(violatedByConstraint));
    }
}
