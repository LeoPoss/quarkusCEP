package de.ur.dao;

import lombok.Data;

import java.util.Map;

@Data
public class GenericEvent {
    private int id;
    private String eventType;
    private long timestamp;
    private Map<String, String> payload;
}
