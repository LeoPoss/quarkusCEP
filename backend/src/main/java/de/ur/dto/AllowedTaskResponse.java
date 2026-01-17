package de.ur.dto;

import java.util.Map;

public record AllowedTaskResponse(
        String task,
        boolean isUnsafe,
        Map<String, String> unsafeConditions) {
    public static AllowedTaskResponse safe(String task) {
        return new AllowedTaskResponse(task, false, null);
    }

    public static AllowedTaskResponse unsafe(String task, Map<String, String> conditions) {
        return new AllowedTaskResponse(task, true, conditions);
    }
}
