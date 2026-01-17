package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event that terminates a test session context partition.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestEndEvent {
    private String testId;
}
