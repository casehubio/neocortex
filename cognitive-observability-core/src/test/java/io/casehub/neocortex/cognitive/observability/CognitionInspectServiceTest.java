package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CognitionInspectServiceTest {

    @Test
    void shouldInspectSingleSubgraph() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("people", "person", null), "t1");
        store.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null))
            .withTraits(java.util.Set.of("Personable")), "t1");
        store.addNode(NodeInput.of("Bob", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.INFERRED, 0.5, null))
            .withTraits(java.util.Set.of("Personable")), "t1");

        var result = CognitionInspectService.inspect(store, "t1", sgId);

        assertEquals(1, result.subgraphStats().size());
        var stat = result.subgraphStats().getFirst();
        assertEquals(2, stat.nodeCount());
        assertEquals("person", stat.subgraphType());
        assertEquals(2L, stat.traitDistribution().get("Personable"));
        assertTrue(stat.avgConfidence() > 0);
    }

    @Test
    void shouldAggregateAcrossSubgraphsWhenNull() {
        var store = new InMemoryMindMapStore();
        String sg1 = store.createSubgraph(new SubgraphInput("people", "person", null), "t1");
        String sg2 = store.createSubgraph(new SubgraphInput("projects", "project", null), "t1");
        store.addNode(NodeInput.of("Alice", sg1)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        store.addNode(NodeInput.of("ProjectX", sg2)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.8, null)), "t1");

        var result = CognitionInspectService.inspect(store, "t1", null);

        assertEquals(2, result.subgraphStats().size());
        int totalNodes = result.subgraphStats().stream()
            .mapToInt(CognitionInspectResult.SubgraphStat::nodeCount).sum();
        assertEquals(2, totalNodes);
    }

    @Test
    void shouldBuildConfidenceHistogram() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        store.addNode(NodeInput.of("Low", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.SPECULATED, 0.1, null)), "t1");
        store.addNode(NodeInput.of("Mid", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.INFERRED, 0.5, null)), "t1");
        store.addNode(NodeInput.of("High", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");

        var result = CognitionInspectService.inspect(store, "t1", sgId);

        assertEquals(5, result.confidenceHistogram().size());
        assertEquals(1, result.confidenceHistogram().get("0.0-0.2"));
        assertEquals(1, result.confidenceHistogram().get("0.4-0.6"));
        assertEquals(1, result.confidenceHistogram().get("0.8-1.0"));
    }

    @Test
    void shouldHandleEmptySubgraph() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("empty", "concept", null), "t1");

        var result = CognitionInspectService.inspect(store, "t1", sgId);

        assertEquals(1, result.subgraphStats().size());
        assertEquals(0, result.subgraphStats().getFirst().nodeCount());
    }
}
