package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CognitionHealthServiceTest {

    @Test
    void shouldReportHealthyGraph() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("people", "person", null), "t1");
        String a = store.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String b = store.addNode(NodeInput.of("Bob", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.8, null)), "t1");
        store.addEdge(EdgeInput.of(a, b, "knows"), "t1");

        var report = CognitionHealthService.health(store, "t1", sgId, 30, 0.3);

        assertEquals(0, report.orphanNodes().size());
        assertEquals(0, report.contradictions().size());
    }

    @Test
    void shouldDetectOrphanNodes() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("people", "person", null), "t1");
        store.addNode(NodeInput.of("Lonely", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");

        var report = CognitionHealthService.health(store, "t1", sgId, 30, 0.3);

        assertEquals(1, report.orphanNodes().size());
        assertEquals("Lonely", report.orphanNodes().getFirst().name());
    }

    @Test
    void shouldFilterSyntheticNodesFromKCores() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("people", "person", null), "t1");
        // Synthetic Summary node
        String summary = store.addNode(NodeInput.of("Summary Node", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null))
            .withTraits(Set.of("Summary")), "t1");
        // Real nodes forming a k-core
        String a = store.addNode(NodeInput.of("A", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String b = store.addNode(NodeInput.of("B", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String c = store.addNode(NodeInput.of("C", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        store.addEdge(EdgeInput.of(a, b, "knows"), "t1");
        store.addEdge(EdgeInput.of(b, c, "knows"), "t1");
        store.addEdge(EdgeInput.of(a, c, "knows"), "t1");
        // Connect summary to the core so it would normally be included
        store.addEdge(EdgeInput.of(summary, a, "summarizes"), "t1");
        store.addEdge(EdgeInput.of(summary, b, "summarizes"), "t1");

        var report = CognitionHealthService.health(store, "t1", sgId, 30, 0.3);

        for (var kCore : report.kCores()) {
            for (String nodeId : kCore.nodeIds()) {
                var node = store.getNode(nodeId, "t1");
                assertFalse(node.traits() != null && node.traits().contains("Summary"),
                    "Summary-trait nodes should be filtered from k-cores");
            }
        }
    }

    @Test
    void shouldAggregateAcrossSubgraphs() {
        var store = new InMemoryMindMapStore();
        String sg1 = store.createSubgraph(new SubgraphInput("people", "person", null), "t1");
        String sg2 = store.createSubgraph(new SubgraphInput("projects", "project", null), "t1");
        store.addNode(NodeInput.of("Alice", sg1)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        store.addNode(NodeInput.of("ProjectX", sg2)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.8, null)), "t1");

        var report = CognitionHealthService.health(store, "t1", null, 30, 0.3);

        assertEquals(2, report.densities().size());
        assertEquals(2, report.orphanNodes().size());
    }

    @Test
    void shouldDetectLowConfidenceNodes() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        store.addNode(NodeInput.of("Weak", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.SPECULATED, 0.1, null)), "t1");
        store.addNode(NodeInput.of("Strong", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");

        var report = CognitionHealthService.health(store, "t1", sgId, 30, 0.3);

        assertEquals(1, report.lowConfidenceClusters().size());
        assertEquals(1, report.lowConfidenceClusters().getFirst().lowConfidence());
        assertEquals(0.5, report.lowConfidenceClusters().getFirst().ratio(), 0.01);
    }
}
