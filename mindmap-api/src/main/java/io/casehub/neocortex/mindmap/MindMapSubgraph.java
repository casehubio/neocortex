package io.casehub.neocortex.mindmap;

import java.time.Instant;

public record MindMapSubgraph(
    String id,
    String name,
    String type,
    String rootNodeId,
    String tenantId,
    Instant createdAt
) {}
