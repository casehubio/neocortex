package io.casehub.neocortex.rag;

import java.util.Map;

public record EdgeStats(
    int coOccurrenceCount,
    double averageScore,
    Map<RetrievalOutcome, Integer> outcomeDistribution
) {
    public EdgeStats {
        if (coOccurrenceCount < 1)
            throw new IllegalArgumentException("coOccurrenceCount must be positive");
        if (outcomeDistribution == null)
            throw new IllegalArgumentException("outcomeDistribution must not be null");
        outcomeDistribution = Map.copyOf(outcomeDistribution);
    }
}
