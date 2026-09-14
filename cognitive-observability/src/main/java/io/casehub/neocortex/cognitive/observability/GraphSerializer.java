package io.casehub.neocortex.cognitive.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraphSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .findAndRegisterModules();

    private GraphSerializer() {}

    public record GraphData(List<NodeData> nodes, List<EdgeData> edges) {}

    public record NodeData(String id, String name, String subgraphType,
                           double confidence, Set<String> traits,
                           Map<String, String> properties) {}

    public record EdgeData(String id, String sourceNodeId, String targetNodeId,
                           String edgeType, double confidence) {}

    public static String toJson(List<? extends MindMapNode> nodes, List<? extends MindMapEdge> edges) {
        var nodeData = nodes.stream()
            .map(n -> new NodeData(n.id(), n.name(), n.subgraphType(),
                n.confidence() != null ? n.confidence().value() : 0.0,
                n.traits(), n.properties()))
            .toList();
        var edgeData = edges.stream()
            .map(e -> new EdgeData(e.id(), e.sourceNodeId(), e.targetNodeId(),
                e.edgeType(), e.confidence() != null ? e.confidence().value() : 0.0))
            .toList();
        try {
            return MAPPER.writeValueAsString(new GraphData(nodeData, edgeData));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize graph", e);
        }
    }

    public static GraphData fromJson(String json) {
        try {
            return MAPPER.readValue(json, GraphData.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize graph", e);
        }
    }

    public static String toMermaid(List<? extends MindMapNode> nodes, List<? extends MindMapEdge> edges) {
        var sb = new StringBuilder("graph TD\n");
        for (var node : nodes) {
            String label = node.name() != null ? node.name() : node.id();
            sb.append("    ").append(sanitizeMermaidId(node.id()))
              .append("[\"").append(escapeMermaid(label)).append("\"]\n");
        }
        for (var edge : edges) {
            sb.append("    ").append(sanitizeMermaidId(edge.sourceNodeId()))
              .append(" -->|").append(escapeMermaid(edge.edgeType()))
              .append("| ").append(sanitizeMermaidId(edge.targetNodeId())).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String sanitizeMermaidId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String escapeMermaid(String text) {
        return text.replace("\"", "&quot;").replace("|", "\\|");
    }
}
