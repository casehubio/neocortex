package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;

import java.util.List;
import java.util.Map;

public record GraphTraversalResult(
        String focusNodeId,
        Map<Integer, List<MindMapNode>> nodesByDepth,
        List<MindMapEdge> edges,
        int totalNodes) {
}
