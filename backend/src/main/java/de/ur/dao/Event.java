package de.ur.dao;

public record Event(String name, EventType type) {
    public enum EventType {
        SIGNAL,
        TASK
    }
}
