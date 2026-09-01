package io.casehub.neocortex.mindmap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeclarativeDerivedEdgeRuleTest {

    @Test
    void directDerivation_inverseEdge() {
        var rule = new DeclarativeDerivedEdgeRule(
            "inverse-knows",
            Set.of("knows"),
            null,
            List.of(new EdgeDerivation("known-by",
                EdgeRef.TRIGGER_TARGET, EdgeRef.TRIGGER_SOURCE,
                null, Map.of())));

        var trigger = new TestEdge("alice", "bob", "knows");
        var derived = rule.derive(new TestNode(Map.of()), trigger, null);

        assertThat(derived).hasSize(1);
        assertThat(derived.get(0).edgeType()).isEqualTo("known-by");
        assertThat(derived.get(0).sourceNodeId()).isEqualTo("bob");
        assertThat(derived.get(0).targetNodeId()).isEqualTo("alice");
    }

    @Test
    void directDerivation_nonMatchingTrigger_returnsEmpty() {
        var rule = new DeclarativeDerivedEdgeRule(
            "inverse-knows",
            Set.of("knows"),
            null,
            List.of(new EdgeDerivation("known-by",
                EdgeRef.TRIGGER_TARGET, EdgeRef.TRIGGER_SOURCE,
                null, Map.of())));

        var trigger = new TestEdge("alice", "bob", "works-with");
        var derived = rule.derive(new TestNode(Map.of()), trigger, null);
        assertThat(derived).isEmpty();
    }

    @Test
    void directDerivation_withProperties() {
        var rule = new DeclarativeDerivedEdgeRule(
            "bidi-colleague",
            Set.of("works-with"),
            null,
            List.of(new EdgeDerivation("works-with",
                EdgeRef.TRIGGER_TARGET, EdgeRef.TRIGGER_SOURCE,
                null, Map.of("derived-reason", "bidirectional"))));

        var trigger = new TestEdge("alice", "bob", "works-with");
        var derived = rule.derive(new TestNode(Map.of()), trigger, null);

        assertThat(derived).hasSize(1);
        assertThat(derived.get(0).properties()).containsEntry("derived-reason", "bidirectional");
    }

    @Test
    void directDerivation_multipleDerivations() {
        var rule = new DeclarativeDerivedEdgeRule(
            "multi-derive",
            Set.of("knows"),
            null,
            List.of(
                new EdgeDerivation("known-by",
                    EdgeRef.TRIGGER_TARGET, EdgeRef.TRIGGER_SOURCE, null, Map.of()),
                new EdgeDerivation("associated-with",
                    EdgeRef.TRIGGER_SOURCE, EdgeRef.TRIGGER_TARGET, null, Map.of())));

        var trigger = new TestEdge("alice", "bob", "knows");
        var derived = rule.derive(new TestNode(Map.of()), trigger, null);

        assertThat(derived).hasSize(2);
        assertThat(derived.get(0).edgeType()).isEqualTo("known-by");
        assertThat(derived.get(1).edgeType()).isEqualTo("associated-with");
    }

    @Test
    void traversalDerivation_singleHop() {
        var rule = new DeclarativeDerivedEdgeRule(
            "descendant-chain",
            Set.of("child-of"),
            new TraversalSpec("child-of", EdgeRef.TRIGGER_TARGET,
                TraversalSpec.TraversalDirection.OUTBOUND, 3),
            List.of(new EdgeDerivation("descendant-of",
                EdgeRef.TRIGGER_SOURCE, EdgeRef.TRAVERSAL_NODE, null, Map.of())));

        // A --child-of--> B --child-of--> C
        var trigger = new TestEdge("a", "b", "child-of");
        var store = new StubMindMapStore(Map.of(
            "b", List.of(new TestEdge("b", "c", "child-of"))
        ));

        var derived = rule.derive(new TestNode(Map.of()), trigger, store);

        assertThat(derived).hasSize(1);
        assertThat(derived.get(0).edgeType()).isEqualTo("descendant-of");
        assertThat(derived.get(0).sourceNodeId()).isEqualTo("a");
        assertThat(derived.get(0).targetNodeId()).isEqualTo("c");
    }

    @Test
    void traversalDerivation_multiHop() {
        var rule = new DeclarativeDerivedEdgeRule(
            "descendant-chain",
            Set.of("child-of"),
            new TraversalSpec("child-of", EdgeRef.TRIGGER_TARGET,
                TraversalSpec.TraversalDirection.OUTBOUND, 3),
            List.of(new EdgeDerivation("descendant-of",
                EdgeRef.TRIGGER_SOURCE, EdgeRef.TRAVERSAL_NODE, null, Map.of())));

        // A --child-of--> B --child-of--> C --child-of--> D
        var trigger = new TestEdge("a", "b", "child-of");
        var store = new StubMindMapStore(Map.of(
            "b", List.of(new TestEdge("b", "c", "child-of")),
            "c", List.of(new TestEdge("c", "d", "child-of"))
        ));

        var derived = rule.derive(new TestNode(Map.of()), trigger, store);

        assertThat(derived).hasSize(2);
        assertThat(derived.get(0).targetNodeId()).isEqualTo("c");
        assertThat(derived.get(1).targetNodeId()).isEqualTo("d");
    }

    @Test
    void traversalDerivation_respectsMaxDepth() {
        var rule = new DeclarativeDerivedEdgeRule(
            "descendant-chain",
            Set.of("child-of"),
            new TraversalSpec("child-of", EdgeRef.TRIGGER_TARGET,
                TraversalSpec.TraversalDirection.OUTBOUND, 1),
            List.of(new EdgeDerivation("descendant-of",
                EdgeRef.TRIGGER_SOURCE, EdgeRef.TRAVERSAL_NODE, null, Map.of())));

        var trigger = new TestEdge("a", "b", "child-of");
        var store = new StubMindMapStore(Map.of(
            "b", List.of(new TestEdge("b", "c", "child-of")),
            "c", List.of(new TestEdge("c", "d", "child-of"))
        ));

        var derived = rule.derive(new TestNode(Map.of()), trigger, store);

        assertThat(derived).hasSize(1);
        assertThat(derived.get(0).targetNodeId()).isEqualTo("c");
    }

    @Test
    void traversalDerivation_preventsCycles() {
        var rule = new DeclarativeDerivedEdgeRule(
            "cycle-test",
            Set.of("links-to"),
            new TraversalSpec("links-to", EdgeRef.TRIGGER_TARGET,
                TraversalSpec.TraversalDirection.OUTBOUND, 10),
            List.of(new EdgeDerivation("reachable",
                EdgeRef.TRIGGER_SOURCE, EdgeRef.TRAVERSAL_NODE, null, Map.of())));

        // A --links-to--> B --links-to--> C --links-to--> B (cycle)
        var trigger = new TestEdge("a", "b", "links-to");
        var store = new StubMindMapStore(Map.of(
            "b", List.of(new TestEdge("b", "c", "links-to")),
            "c", List.of(new TestEdge("c", "b", "links-to"))
        ));

        var derived = rule.derive(new TestNode(Map.of()), trigger, store);

        assertThat(derived).hasSize(2);
    }

    @Test
    void traversalDerivation_nullStore_returnsEmpty() {
        var rule = new DeclarativeDerivedEdgeRule(
            "descendant-chain",
            Set.of("child-of"),
            new TraversalSpec("child-of", EdgeRef.TRIGGER_TARGET,
                TraversalSpec.TraversalDirection.OUTBOUND, 3),
            List.of(new EdgeDerivation("descendant-of",
                EdgeRef.TRIGGER_SOURCE, EdgeRef.TRAVERSAL_NODE, null, Map.of())));

        var trigger = new TestEdge("a", "b", "child-of");
        var derived = rule.derive(new TestNode(Map.of()), trigger, null);

        assertThat(derived).isEmpty();
    }
}
