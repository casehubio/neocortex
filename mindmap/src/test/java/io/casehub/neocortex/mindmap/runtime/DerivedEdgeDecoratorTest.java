package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DerivedEdgeDecoratorTest {

    private InMemoryMindMapStore store;
    private DerivedEdgeDecorator decorator;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        decorator = new DerivedEdgeDecorator(store, List.of(new InverseEdgeRule()));
        subgraphId = store.createSubgraph(
            new SubgraphInput("Test", SubgraphType.GENERAL, null), "t1");
    }

    @Test
    void addEdge_firesRuleAndCreatesDerivedEdge() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        decorator.addEdge(edge(alice, bob, "has-child"), "t1");

        List<MindMapEdge> bobEdges = decorator.neighbors(bob, "parent-of", "t1");
        assertThat(bobEdges).hasSize(1);
        MindMapEdge derived = bobEdges.get(0);
        assertThat(derived.sourceNodeId()).isEqualTo(bob);
        assertThat(derived.targetNodeId()).isEqualTo(alice);
        assertThat(derived.edgeType()).isEqualTo("parent-of");
    }

    @Test
    void addEdge_derivedEdgeCarriesProvenance() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        String triggerId = decorator.addEdge(edge(alice, bob, "has-child"), "t1");

        List<MindMapEdge> derived = decorator.neighbors(bob, "parent-of", "t1");
        assertThat(derived).hasSize(1);
        MindMapEdge d = derived.get(0);
        assertThat(d.properties().get(DerivedEdgeRule.PROPERTY_DERIVED)).isEqualTo("true");
        assertThat(d.properties().get(DerivedEdgeRule.PROPERTY_TRIGGER_EDGE_ID)).isEqualTo(triggerId);
        assertThat(d.properties().get(DerivedEdgeRule.PROPERTY_RULE_NAME)).isEqualTo("inverse-edge");
    }

    @Test
    void addEdge_noMatchingRule_noDerivedEdges() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        decorator.addEdge(edge(alice, bob, "knows"), "t1");

        List<MindMapEdge> allEdges = decorator.neighbors(alice, "t1");
        assertThat(allEdges).hasSize(1);
        assertThat(allEdges.get(0).edgeType()).isEqualTo("knows");
    }

    @Test
    void removeEdge_retractsDerivedEdges() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        String triggerId = decorator.addEdge(edge(alice, bob, "has-child"), "t1");
        assertThat(decorator.neighbors(bob, "parent-of", "t1")).hasSize(1);

        decorator.removeEdge(triggerId, "t1");

        assertThat(decorator.neighbors(bob, "parent-of", "t1")).isEmpty();
    }

    @Test
    void removeEdge_cascadesRetraction() {
        // Rule: has-child → parent-of (reverse). CascadeRule: parent-of → ancestor-of (same dir)
        decorator = new DerivedEdgeDecorator(store,
            List.of(new InverseEdgeRule(), new CascadeRule()));

        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        String triggerId = decorator.addEdge(edge(alice, bob, "has-child"), "t1");

        // has-child → parent-of (via InverseEdgeRule)
        // parent-of → ancestor-of (via CascadeRule)
        assertThat(decorator.neighbors(bob, "parent-of", "t1")).hasSize(1);
        assertThat(decorator.neighbors(bob, "ancestor-of", "t1")).hasSize(1);

        decorator.removeEdge(triggerId, "t1");

        assertThat(decorator.neighbors(bob, "parent-of", "t1")).isEmpty();
        assertThat(decorator.neighbors(bob, "ancestor-of", "t1")).isEmpty();
    }

    @Test
    void addEdge_cyclePreventionStopsAtMaxDepth() {
        // PingPongRule: "ping" → "pong" (reverse), "pong" → "ping" (reverse)
        // Without depth limit this would loop forever
        decorator = new DerivedEdgeDecorator(store, List.of(new PingPongRule()), 3);

        String a = decorator.addNode(node("A"), "t1");
        String b = decorator.addNode(node("B"), "t1");

        decorator.addEdge(edge(a, b, "ping"), "t1");

        // depth 0: ping A→B (original)
        // depth 1: pong B→A (derived from ping)
        // depth 2: ping A→B (derived from pong) — but same as original? No, it's a new edge
        // depth 3: would be pong B→A again — stopped by max depth
        List<MindMapEdge> allA = decorator.neighbors(a, "t1");
        List<MindMapEdge> allB = decorator.neighbors(b, "t1");
        int totalEdges = (int) (allA.stream().map(MindMapEdge::id).count()
            + allB.stream().map(MindMapEdge::id).count());
        // edges are double-counted (each appears in both endpoints' neighbors)
        // With max depth 3: original + 3 derived = 4 edges, each counted twice = 8
        // But let's just check total unique edges
        var allIds = new java.util.HashSet<String>();
        allA.forEach(e -> allIds.add(e.id()));
        allB.forEach(e -> allIds.add(e.id()));
        assertThat(allIds).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void multipleRules_allFire() {
        decorator = new DerivedEdgeDecorator(store,
            List.of(new InverseEdgeRule(), new CascadeRule()));

        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        decorator.addEdge(edge(alice, bob, "has-child"), "t1");

        assertThat(decorator.neighbors(bob, "parent-of", "t1")).hasSize(1);
        assertThat(decorator.neighbors(bob, "ancestor-of", "t1")).hasSize(1);
    }

    @Test
    void removeEdge_onlyRetractsOwnDerived() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");
        String carol = decorator.addNode(node("Carol"), "t1");

        String trigger1 = decorator.addEdge(edge(alice, bob, "has-child"), "t1");
        String trigger2 = decorator.addEdge(edge(alice, carol, "has-child"), "t1");

        assertThat(decorator.neighbors(bob, "parent-of", "t1")).hasSize(1);
        assertThat(decorator.neighbors(carol, "parent-of", "t1")).hasSize(1);

        decorator.removeEdge(trigger1, "t1");

        assertThat(decorator.neighbors(bob, "parent-of", "t1")).isEmpty();
        assertThat(decorator.neighbors(carol, "parent-of", "t1")).hasSize(1);
    }

    @Test
    void derivedEdge_confidenceInheritsInferredOrigin() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        decorator.addEdge(edge(alice, bob, "has-child"), "t1");

        List<MindMapEdge> derived = decorator.neighbors(bob, "parent-of", "t1");
        assertThat(derived).hasSize(1);
        assertThat(derived.get(0).confidenceOrigin()).isEqualTo(ConfidenceOrigin.INFERRED);
    }

    @Test
    void removeNonTriggerEdge_passesThrough() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob   = decorator.addNode(node("Bob"), "t1");

        String plainEdge = decorator.addEdge(edge(alice, bob, "knows"), "t1");
        decorator.removeEdge(plainEdge, "t1");

        assertThat(decorator.neighbors(alice, "t1")).isEmpty();
    }

    @Test
    void emptyRuleList_purePassthrough() {
        DerivedEdgeDecorator noRules = new DerivedEdgeDecorator(store, List.of());

        String alice = noRules.addNode(node("Alice"), "t1");
        String bob   = noRules.addNode(node("Bob"), "t1");

        noRules.addEdge(edge(alice, bob, "has-child"), "t1");

        List<MindMapEdge> allEdges = noRules.neighbors(alice, "t1");
        assertThat(allEdges).hasSize(1);
        assertThat(allEdges.get(0).edgeType()).isEqualTo("has-child");
    }

    @Test
    void eraseNode_retractsDerivedEdgesFromMap() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob   = decorator.addNode(node("Bob"), "t1");

        decorator.addEdge(edge(alice, bob, "has-child"), "t1");
        assertThat(decorator.neighbors(bob, "parent-of", "t1")).hasSize(1);

        decorator.eraseNode(alice, "t1");

        // eraseNode cascades edge deletion internally — derived edges
        // should also be cleaned up from the trigger map
        assertThat(decorator.neighbors(bob, "parent-of", "t1")).isEmpty();
    }


    // --- Test rules ---

    static class InverseEdgeRule implements DerivedEdgeRule {
        @Override
        public String name() { return "inverse-edge"; }

        @Override
        public List<EdgeInput> derive(MindMapNode sourceNode, MindMapEdge trigger, MindMapStore store) {
            if ("has-child".equals(trigger.edgeType())) {
                return List.of(new EdgeInput(
                    trigger.targetNodeId(), trigger.sourceNodeId(),
                    "parent-of", ConfidenceOrigin.INFERRED, null,
                    "derived", null, null, null, null, null, null));
            }
            return List.of();
        }
    }

    static class CascadeRule implements DerivedEdgeRule {
        @Override
        public String name() { return "cascade-rule"; }

        @Override
        public List<EdgeInput> derive(MindMapNode sourceNode, MindMapEdge trigger, MindMapStore store) {
            if ("parent-of".equals(trigger.edgeType())) {
                return List.of(new EdgeInput(
                    trigger.sourceNodeId(), trigger.targetNodeId(),
                    "ancestor-of", ConfidenceOrigin.INFERRED, null,
                    "derived", null, null, null, null, null, null));
            }
            return List.of();
        }
    }

    static class PingPongRule implements DerivedEdgeRule {
        @Override
        public String name() { return "ping-pong"; }

        @Override
        public List<EdgeInput> derive(MindMapNode sourceNode, MindMapEdge trigger, MindMapStore store) {
            if ("ping".equals(trigger.edgeType())) {
                return List.of(new EdgeInput(
                    trigger.targetNodeId(), trigger.sourceNodeId(),
                    "pong", ConfidenceOrigin.INFERRED, null,
                    "derived", null, null, null, null, null, null));
            }
            if ("pong".equals(trigger.edgeType())) {
                return List.of(new EdgeInput(
                    trigger.targetNodeId(), trigger.sourceNodeId(),
                    "ping", ConfidenceOrigin.INFERRED, null,
                    "derived", null, null, null, null, null, null));
            }
            return List.of();
        }
    }

    private NodeInput node(String name) {
        return new NodeInput(name, subgraphId, ConfidenceOrigin.STATED, null,
            "test", null, null, null, null, null, null, null, null);
    }

    private EdgeInput edge(String source, String target, String type) {
        return new EdgeInput(source, target, type, ConfidenceOrigin.STATED, null,
            "test", null, null, null, null, null, null);
    }
}
