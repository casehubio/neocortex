package io.casehub.neocortex.mindmap.intelligence;

public record CuriositySignal(
    SignalCategory category,
    double score,
    String targetNodeId,
    String targetSubgraphId,
    String question,
    String description
) {}
