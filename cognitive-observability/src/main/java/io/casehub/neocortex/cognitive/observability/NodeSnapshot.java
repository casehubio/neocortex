package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeRef;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record NodeSnapshot(
    String id,
    String name,
    String subgraphType,
    Confidence confidence,
    Double pleasure,
    Double arousal,
    Double dominance,
    Set<String> traits,
    Set<NodeRef> refs,
    Map<String, String> properties,
    Instant createdAt,
    Instant updatedAt
) {
    public static NodeSnapshot from(MindMapNode node) {
        return new NodeSnapshot(
            node.id(), node.name(), node.subgraphType(),
            node.confidence(), node.pleasure(), node.arousal(), node.dominance(),
            node.traits(), node.refs(), node.properties(),
            node.createdAt(), node.updatedAt());
    }
}
