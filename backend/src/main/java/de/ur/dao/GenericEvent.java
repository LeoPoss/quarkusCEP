package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class GenericEvent {
    private String id;
    private String eventType;
    private long timestamp;
    private Map<String, String> payload;
}
