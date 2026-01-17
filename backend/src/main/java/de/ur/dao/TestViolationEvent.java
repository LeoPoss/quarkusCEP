package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event emitted when a test determines a violation would occur.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestViolationEvent {
    private String testId;
    private String constraintName;
    private String constraintType;
    private String hypotheticalEvent;
}
