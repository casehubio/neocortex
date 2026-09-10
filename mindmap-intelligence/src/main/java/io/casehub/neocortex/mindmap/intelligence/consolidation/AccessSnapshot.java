package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.time.Instant;
import java.util.Map;

public record AccessSnapshot(
    Map<String, Long> counts,
    Map<String, Instant> lastAccessTimes
) {
    public AccessSnapshot {
        counts = Map.copyOf(counts);
        lastAccessTimes = Map.copyOf(lastAccessTimes);
    }

    public static final AccessSnapshot EMPTY = new AccessSnapshot(Map.of(), Map.of());
}
