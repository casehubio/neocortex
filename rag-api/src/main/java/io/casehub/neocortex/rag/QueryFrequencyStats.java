package io.casehub.neocortex.rag;

import java.time.Instant;

public record QueryFrequencyStats(
        int count,
        double averageScore,
        Instant firstSeen,
        Instant lastSeen) {

    public QueryFrequencyStats {
        if (count < 1) throw new IllegalArgumentException("count must be positive");
        if (firstSeen == null) throw new IllegalArgumentException("firstSeen must not be null");
        if (lastSeen == null) throw new IllegalArgumentException("lastSeen must not be null");
    }
}
