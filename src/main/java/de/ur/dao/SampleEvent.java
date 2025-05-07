package de.ur.dao;

import lombok.Data;

@Data
public class SampleEvent {
    private int id;
    private String type;
    private double value;
    private long timestamp;
}
