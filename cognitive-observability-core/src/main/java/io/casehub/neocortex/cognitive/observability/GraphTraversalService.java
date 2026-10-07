package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

final class GraphTraversalService {

    private GraphTraversalService() {}

    static GraphTraversalResult traverse(MindMapStore store, String tenantId,
                                          String focusNodeId, int maxDepth,
                                          Double minConfidence) {
        Map<Integer, List<MindMapNode>> nodesByDepth = new LinkedHashMap<>();
        List<MindMapEdge> allEdges = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<TraversalEntry> queue = new ArrayDeque<>();

        MindMapNode focusNode = store.getNode(focusNodeId, tenantId);
        if (focusNode == null) {
            return new GraphTraversalResult(focusNodeId, Map.of(), List.of(), 0);
        }

        visited.add(focusNodeId);
        nodesByDepth.computeIfAbsent(0, k -> new ArrayList<>()).add(focusNode);
        queue.add(new TraversalEntry(focusNodeId, 0));

        while (!queue.isEmpty()) {
            TraversalEntry entry = queue.poll();
            if (entry.depth >= maxDepth) continue;

            List<MindMapEdge> edges = store.neighbors(entry.nodeId, tenantId);
            for (MindMapEdge edge : edges) {
                if (minConfidence != null && edge.confidence() != null
                    && edge.confidence().value() < minConfidence) {
                    continue;
                }

                String neighborId = edge.sourceNodeId().equals(entry.nodeId)
                                    ? edge.targetNodeId() : edge.sourceNodeId();
                if (visited.contains(neighborId)) {
                    allEdges.add(edge);
                    continue;
                }

                MindMapNode neighbor = store.getNode(neighborId, tenantId);
                if (neighbor == null) continue;

                visited.add(neighborId);
                int nextDepth = entry.depth + 1;
                nodesByDepth.computeIfAbsent(nextDepth, k -> new ArrayList<>()).add(neighbor);
                allEdges.add(edge);
                queue.add(new TraversalEntry(neighborId, nextDepth));
            }
        }

        int total = nodesByDepth.values().stream().mapToInt(List::size).sum();
        return new GraphTraversalResult(focusNodeId, nodesByDepth, allEdges, total);
    }

    private record TraversalEntry(String nodeId, int depth) {}
}
