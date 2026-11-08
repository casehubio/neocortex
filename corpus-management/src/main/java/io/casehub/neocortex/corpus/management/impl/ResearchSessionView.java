package io.casehub.neocortex.corpus.management.impl;

import java.time.Instant;
import java.util.List;

public record ResearchSessionView(
    String id,
    String name,
    String topic,
    String state,
    String subgraph,
    Instant createdAt,
    Instant lastActive,
    List<FindingSummary> findings
) {
    public record FindingSummary(String entryId, String topic) {}
}
