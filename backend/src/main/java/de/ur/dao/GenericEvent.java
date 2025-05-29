package de.ur.dao;

import lombok.Data;

import java.util.Map;

@Data
public class GenericEvent {
    private int id;
    private String type;
    private long timestamp;
    private Map<String, String> payload;
}
