package io.casehub.neocortex.mindmap.intelligence;

public record CuriositySignal(
    SignalCategory category,
    double score,
    String targetNodeId,
    String targetSubgraphType,
    String question,
    String description
) {}
