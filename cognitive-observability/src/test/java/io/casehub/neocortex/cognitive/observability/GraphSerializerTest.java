package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphSerializerTest {

    @Test
    void shouldRoundTripJson() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String a = store.addNode(NodeInput.of("Alpha", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String b = store.addNode(NodeInput.of("Beta", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.8, null)), "t1");
        store.addEdge(EdgeInput.of(a, b, "relates_to"), "t1");

        var nodes = store.nodesIn(sgId, "t1");
        var edges = store.neighbors(a, "t1");

        String json = GraphSerializer.toJson(nodes, edges);
        assertNotNull(json);
        assertTrue(json.contains("Alpha"));
        assertTrue(json.contains("relates_to"));

        var data = GraphSerializer.fromJson(json);
        assertEquals(2, data.nodes().size());
        assertEquals(1, data.edges().size());
        assertEquals("relates_to", data.edges().getFirst().edgeType());
    }

    @Test
    void shouldProduceMermaidSyntax() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String a = store.addNode(NodeInput.of("Alpha", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String b = store.addNode(NodeInput.of("Beta", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.8, null)), "t1");
        store.addEdge(EdgeInput.of(a, b, "relates_to"), "t1");

        var nodes = store.nodesIn(sgId, "t1");
        var edges = store.neighbors(a, "t1");

        String mermaid = GraphSerializer.toMermaid(nodes, edges);

        assertTrue(mermaid.startsWith("graph TD"));
        assertTrue(mermaid.contains("Alpha"));
        assertTrue(mermaid.contains("Beta"));
        assertTrue(mermaid.contains("relates_to"));
    }

    @Test
    void shouldHandleEmptyGraph() {
        String json = GraphSerializer.toJson(java.util.List.of(), java.util.List.of());
        var data = GraphSerializer.fromJson(json);
        assertTrue(data.nodes().isEmpty());
        assertTrue(data.edges().isEmpty());

        String mermaid = GraphSerializer.toMermaid(java.util.List.of(), java.util.List.of());
        assertEquals("graph TD", mermaid);
    }
}
