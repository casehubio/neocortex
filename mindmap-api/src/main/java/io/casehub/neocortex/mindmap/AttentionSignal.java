package io.casehub.neocortex.mindmap;

public record AttentionSignal(
    String principalId,
    String tenantId,
    SignalCategory category,
    String sourceNodeId,
    String sourceName,
    double significance,
    String reason
) {}
