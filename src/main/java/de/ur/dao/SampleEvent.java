package de.ur.dao;

import lombok.Data;

@Data
public class SampleEvent {
    private String id;
    private double value;
    private long timestamp;
}
