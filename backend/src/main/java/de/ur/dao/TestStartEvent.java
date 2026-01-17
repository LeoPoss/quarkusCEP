package de.ur.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event that initiates a test session context partition.
 * Used for hypothetical event analysis.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestStartEvent {
    private String testId;
    private String constraintName;
    private String currentStatus; // Current constraint status (INIT, TEMPORARY_VIOLATION, etc.)
    private String hypotheticalEvent; // The event we're testing
    private boolean hasActivation; // Whether activation has occurred in trace
    private String lastEventType; // Last event type in trace (for chain constraints)
}
