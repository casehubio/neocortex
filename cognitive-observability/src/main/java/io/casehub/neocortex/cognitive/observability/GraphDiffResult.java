package io.casehub.neocortex.cognitive.observability;

import java.time.Instant;
import java.util.List;

public record GraphDiffResult(
    List<GraphMutation> mutations,
    DiffSummary summary,
    TimeRange timeRange
) {
    public record DiffSummary(
        int nodesAdded,
        int nodesUpdated,
        int nodesErased,
        int edgesAdded,
        int edgesRemoved,
        int merges,
        int supersessions,
        int reinstated
    ) {}

    public record TimeRange(Instant from, Instant to) {}
}
