package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TraitApplicationDecoratorTest {

    private InMemoryMindMapStore store;
    private TraitApplicationDecorator decorator;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        decorator = new TraitApplicationDecorator(store, List.of(new PersonableRule()));
        subgraphId = store.createSubgraph(
            new SubgraphInput("Test", SubgraphType.GENERAL, null), "t1");
    }

    @Test
    void addNode_withMatchingProperties_appliesTrait() {
        String id = decorator.addNode(
            new NodeInput("Alice", subgraphId, null,
                "test", null, null, null, null, null, null, null,
                Map.of("birthday", "1990-01-15")), "t1");

        MindMapNode node = decorator.getNode(id, "t1");
        assertThat(node.traits()).contains("Personable");
    }

    @Test
    void addNode_withoutMatchingProperties_noTrait() {
        String id = decorator.addNode(node("Project-X"), "t1");

        MindMapNode node = decorator.getNode(id, "t1");
        assertThat(node.traits()).doesNotContain("Personable");
    }

    @Test
    void addEdge_matchingEdgeType_appliesTraitToSource() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        decorator.addEdge(edge(alice, bob, "parent-of"), "t1");

        MindMapNode aliceNode = decorator.getNode(alice, "t1");
        assertThat(aliceNode.traits()).contains("Personable");
    }

    @Test
    void removeEdge_lastEvidence_retractsTrait() {
        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        String edgeId = decorator.addEdge(edge(alice, bob, "parent-of"), "t1");
        assertThat(decorator.getNode(alice, "t1").traits()).contains("Personable");

        decorator.removeEdge(edgeId, "t1");

        assertThat(decorator.getNode(alice, "t1").traits()).doesNotContain("Personable");
    }

    @Test
    void updateNode_addProperty_appliesTrait() {
        String alice = decorator.addNode(node("Alice"), "t1");
        assertThat(decorator.getNode(alice, "t1").traits()).doesNotContain("Personable");

        decorator.updateNode(alice,
            new NodeUpdate(null, null, null, null, null, null,
                null, null, null, null, null,
                Map.of("birthday", "1990-01-15"), null), "t1");

        assertThat(decorator.getNode(alice, "t1").traits()).contains("Personable");
    }

    @Test
    void updateNode_removeProperty_retractsTrait() {
        String alice = decorator.addNode(
            new NodeInput("Alice", subgraphId, null,
                "test", null, null, null, null, null, null, null,
                Map.of("birthday", "1990-01-15")), "t1");
        assertThat(decorator.getNode(alice, "t1").traits()).contains("Personable");

        decorator.updateNode(alice,
            new NodeUpdate(null, null, null, null, null, null,
                null, null, null, null, null,
                null, Set.of("birthday")), "t1");

        assertThat(decorator.getNode(alice, "t1").traits()).doesNotContain("Personable");
    }

    @Test
    void conflictResolution_applicationWinsOverRetraction() {
        decorator = new TraitApplicationDecorator(store,
            List.of(new PersonableRule(), new EmailPersonableRule()));

        String alice = decorator.addNode(
            new NodeInput("Alice", subgraphId, null,
                "test", null, null, null, null, null, null, null,
                Map.of("birthday", "1990-01-15")), "t1");

        assertThat(decorator.getNode(alice, "t1").traits()).contains("Personable");
    }

    @Test
    void reentrancyGuard_traitMutationDoesNotRetriggerEvaluation() {
        var countingRule = new CountingRule();
        decorator = new TraitApplicationDecorator(store, List.of(countingRule));

        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");
        countingRule.callCount = 0;

        decorator.addEdge(edge(alice, bob, "parent-of"), "t1");

        assertThat(countingRule.callCount).isEqualTo(2);
    }

    @Test
    void emptyRuleList_purePassthrough() {
        TraitApplicationDecorator noRules = new TraitApplicationDecorator(store, List.of());

        String alice = noRules.addNode(
            new NodeInput("Alice", subgraphId, null,
                "test", null, null, null, null, null, null, null,
                Map.of("birthday", "1990-01-15")), "t1");

        assertThat(noRules.getNode(alice, "t1").traits()).isEmpty();
    }

    @Test
    void multipleRules_differentTraits_bothApplied() {
        decorator = new TraitApplicationDecorator(store,
            List.of(new PersonableRule(), new ProjectlikeRule()));

        String node = decorator.addNode(
            new NodeInput("Neocortex", subgraphId, null,
                "test", null, null, null, null, null, null, null,
                Map.of("birthday", "n/a", "status", "active")), "t1");

        MindMapNode result = decorator.getNode(node, "t1");
        assertThat(result.traits()).contains("Personable", "Projectlike");
    }

    @Test
    void decoratorChain_derivedEdgeTriggersTrait() {
        var derivedDecorator = new DerivedEdgeDecorator(store,
            List.of(new InverseEdgeRule()));
        decorator = new TraitApplicationDecorator(derivedDecorator, List.of(new PersonableRule()));

        String alice = decorator.addNode(node("Alice"), "t1");
        String bob = decorator.addNode(node("Bob"), "t1");

        decorator.addEdge(edge(alice, bob, "has-child"), "t1");

        assertThat(decorator.getNode(bob, "t1").traits()).contains("Personable");
    }


    // --- Test rules ---

    static class PersonableRule implements TraitRule {
        @Override public String traitName() { return "Personable"; }

        @Override
        public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
            boolean hasProperties = node.property("birthday").isPresent()
                || node.property("role").isPresent()
                || node.property("email").isPresent();
            boolean hasEdges = edges.stream()
                .anyMatch(e -> "parent-of".equals(e.edgeType())
                    || "child-of".equals(e.edgeType())
                    || "works-at".equals(e.edgeType()));
            return hasProperties || hasEdges;
        }
    }

    static class EmailPersonableRule implements TraitRule {
        @Override public String traitName() { return "Personable"; }

        @Override
        public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
            return node.property("email").isPresent();
        }
    }

    static class ProjectlikeRule implements TraitRule {
        @Override public String traitName() { return "Projectlike"; }

        @Override
        public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
            return node.property("status").isPresent();
        }
    }

    static class CountingRule implements TraitRule {
        int callCount = 0;
        @Override public String traitName() { return "Personable"; }

        @Override
        public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
            callCount++;
            return edges.stream().anyMatch(e -> "parent-of".equals(e.edgeType()));
        }
    }

    static class InverseEdgeRule implements DerivedEdgeRule {
        @Override public String name() { return "inverse-edge"; }

        @Override
        public List<EdgeInput> derive(MindMapNode sourceNode, MindMapEdge trigger, MindMapStore store) {
            if ("has-child".equals(trigger.edgeType())) {
                return List.of(new EdgeInput(
                    trigger.targetNodeId(), trigger.sourceNodeId(),
                    "parent-of", Confidence.inferred(0.7, Instant.now()),
                    "derived", null, null, null, null, null, null));
            }
            return List.of();
        }
    }

    private NodeInput node(String name) {
        return new NodeInput(name, subgraphId, null,
            "test", null, null, null, null, null, null, null, null);
    }

    private EdgeInput edge(String source, String target, String type) {
        return new EdgeInput(source, target, type, null,
            "test", null, null, null, null, null, null);
    }
}
