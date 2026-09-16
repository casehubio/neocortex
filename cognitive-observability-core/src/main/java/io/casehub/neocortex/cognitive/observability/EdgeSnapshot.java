package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.ValidationTier;

import java.time.Instant;

public record EdgeSnapshot(
    String id,
    String sourceNodeId,
    String targetNodeId,
    String edgeType,
    ValidationTier tier,
    Confidence confidence,
    Instant createdAt,
    Instant updatedAt
) {
    public static EdgeSnapshot from(MindMapEdge edge) {
        return new EdgeSnapshot(
            edge.id(), edge.sourceNodeId(), edge.targetNodeId(),
            edge.edgeType(), edge.tier(), edge.confidence(),
            edge.createdAt(), edge.updatedAt());
    }
}
