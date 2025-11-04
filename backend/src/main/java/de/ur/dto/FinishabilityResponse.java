package de.ur.dto;

import java.util.List;

public record FinishabilityResponse(boolean canFinish, List<String> reasons) {
    public FinishabilityResponse(boolean canFinish) {
        this(canFinish, List.of());
    }
}
