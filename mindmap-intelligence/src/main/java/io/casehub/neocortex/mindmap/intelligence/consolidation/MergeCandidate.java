package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.time.Instant;

public record MergeCandidate(
    String nodeId1,
    String nodeId2,
    double score,
    String reason,
    Instant detectedAt
) {}
