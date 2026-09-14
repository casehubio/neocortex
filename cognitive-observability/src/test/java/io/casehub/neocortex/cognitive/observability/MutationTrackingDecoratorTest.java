package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MutationContext;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MutationTrackingDecoratorTest {

    private InMemoryMindMapStore store;
    private List<GraphMutation> mutations;
    private MutationTrackingDecorator decorator;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        mutations = new ArrayList<>();
        decorator = new MutationTrackingDecorator(store, null, evt -> mutations.add(evt.mutation()));
    }

    @AfterEach
    void cleanup() {
        MutationContext.clear();
    }

    @Test
    void shouldCaptureNodeAdded() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        MutationContext.set("test-source");
        String nodeId = decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");

        assertEquals(2, mutations.size());
        assertInstanceOf(GraphMutation.SubgraphCreated.class, mutations.get(0));

        var nodeAdded = (GraphMutation.NodeAdded) mutations.get(1);
        assertEquals(nodeId, nodeAdded.nodeId());
        assertEquals("Alice", nodeAdded.name());
        assertEquals("test-source", nodeAdded.source());
        assertEquals(sgId, nodeAdded.subgraphId());
    }

    @Test
    void shouldCaptureNodeUpdatedWithFieldChanges() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String nodeId = decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        mutations.clear();

        decorator.updateNode(nodeId, NodeUpdate.empty().withName("Alicia"), "t1");

        assertEquals(1, mutations.size());
        var updated = (GraphMutation.NodeUpdated) mutations.getFirst();
        assertEquals(nodeId, updated.nodeId());
        assertTrue(updated.changes().containsKey("name"));
        assertEquals("Alice", updated.changes().get("name").oldValue());
        assertEquals("Alicia", updated.changes().get("name").newValue());
    }

    @Test
    void shouldNotCaptureNoOpUpdate() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String nodeId = decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        mutations.clear();

        decorator.updateNode(nodeId, NodeUpdate.empty(), "t1");

        assertTrue(mutations.isEmpty());
    }

    @Test
    void shouldCaptureEdgeAddedAndRemoved() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String a = decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String b = decorator.addNode(NodeInput.of("Bob", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        mutations.clear();

        String edgeId = decorator.addEdge(EdgeInput.of(a, b, "knows"), "t1");
        var edgeAdded = (GraphMutation.EdgeAdded) mutations.getFirst();
        assertEquals(edgeId, edgeAdded.edgeId());
        assertEquals("knows", edgeAdded.edgeType());

        mutations.clear();
        decorator.removeEdge(edgeId, "t1");
        var edgeRemoved = (GraphMutation.EdgeRemoved) mutations.getFirst();
        assertEquals(edgeId, edgeRemoved.edgeId());
        assertEquals(a, edgeRemoved.sourceNodeId());
        assertEquals(b, edgeRemoved.targetNodeId());
    }

    @Test
    void shouldCaptureMerge() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String a = decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String b = decorator.addNode(NodeInput.of("Alicia", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.8, null)), "t1");
        mutations.clear();

        decorator.mergeNodes(a, b, "t1");

        assertEquals(1, mutations.size());
        var merged = (GraphMutation.NodesMerged) mutations.getFirst();
        assertEquals(a, merged.survivorId());
        assertEquals(b, merged.absorbedId());
    }

    @Test
    void shouldCaptureSupersessionAndReinstatement() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String a = decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String b = decorator.addNode(NodeInput.of("Alice-new", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        mutations.clear();

        decorator.supersede(a, b, "duplicate", "t1");
        var superseded = (GraphMutation.NodeSuperseded) mutations.getFirst();
        assertEquals(a, superseded.supersededId());
        assertEquals(b, superseded.supersedingId());
        assertEquals("duplicate", superseded.reason());

        mutations.clear();
        decorator.reinstate(a, "t1");
        var reinstated = (GraphMutation.NodeReinstated) mutations.getFirst();
        assertEquals(a, reinstated.nodeId());
    }

    @Test
    void shouldCaptureAliasOperations() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String nodeId = decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        mutations.clear();

        decorator.addAlias(nodeId, "Ali", "t1");
        assertInstanceOf(GraphMutation.AliasAdded.class, mutations.getFirst());
        assertEquals("Ali", ((GraphMutation.AliasAdded) mutations.getFirst()).alias());

        mutations.clear();
        decorator.removeAlias(nodeId, "Ali", "t1");
        assertInstanceOf(GraphMutation.AliasRemoved.class, mutations.getFirst());
    }

    @Test
    void shouldRouteEraseEntityAcrossTenantsThroughDecorator() {
        String sg1 = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        decorator.addNode(NodeInput.of("Alice", sg1)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        String sg2 = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t2");
        decorator.addNode(NodeInput.of("Alice", sg2)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t2");
        mutations.clear();

        decorator.eraseEntityAcrossTenants("Alice", Set.of("t1", "t2"));

        long entityErasedCount = mutations.stream()
            .filter(m -> m instanceof GraphMutation.EntityErased).count();
        assertEquals(2, entityErasedCount);
    }

    @Test
    void shouldTagMutationsWithSourceContext() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        assertEquals("manual", mutations.getFirst().source());

        mutations.clear();
        MutationContext.set("consolidation:MergeDetectionPhase");
        decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        assertEquals("consolidation:MergeDetectionPhase", mutations.getFirst().source());
    }

    @Test
    void shouldCaptureEraseSubgraph() {
        String sgId = decorator.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        decorator.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        mutations.clear();

        decorator.eraseSubgraph(sgId, "t1");

        assertEquals(1, mutations.size());
        assertInstanceOf(GraphMutation.SubgraphErased.class, mutations.getFirst());
    }
}
