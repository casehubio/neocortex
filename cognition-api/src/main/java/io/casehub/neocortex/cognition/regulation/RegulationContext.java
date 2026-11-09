package io.casehub.neocortex.cognition.regulation;

import org.jspecify.annotations.Nullable;
import java.util.List;

public record RegulationContext(
        @Nullable String location,
        @Nullable List<String> interactingAgents,
        @Nullable String topic
) {
    public boolean matchesDimension(@Nullable String candidateLocation,
                                     ProactiveAvoidanceMode mode) {
        if (location == null || candidateLocation == null) return false;
        if (!location.equalsIgnoreCase(candidateLocation)) return false;
        return true;
    }

    public boolean matchesAgents(@Nullable List<String> candidateAgents) {
        if (interactingAgents == null || candidateAgents == null) return false;
        return interactingAgents.stream().anyMatch(candidateAgents::contains);
    }
}
