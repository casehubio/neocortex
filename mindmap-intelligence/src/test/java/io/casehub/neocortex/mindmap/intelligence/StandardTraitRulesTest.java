package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StandardTraitRulesTest {

    private InMemoryMindMapStore store;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        subgraphId = store.createSubgraph(
            new SubgraphInput("Test", SubgraphType.GENERAL, null), "t1");
    }

    @Test
    void personableRule_matchesBirthday() {
        String id = store.addNode(new NodeInput("Alice", subgraphId,
            null, "test", null, null,
            null, null, null, null, null,
            Map.of("birthday", "1990-01-15")), "t1");
        MindMapNode node = store.getNode(id, "t1");

        var rule = new PersonableTraitRule();
        assertThat(rule.traitName()).isEqualTo("Personable");
        assertThat(rule.matches(node, List.of())).isTrue();
    }

    @Test
    void personableRule_matchesParentOfEdge() {
        String alice = store.addNode(node("Alice"), "t1");
        String bob = store.addNode(node("Bob"), "t1");
        String edgeId = store.addEdge(edge(alice, bob, "parent-of"), "t1");

        MindMapNode aliceNode = store.getNode(alice, "t1");
        MindMapEdge e = store.getEdge(edgeId, "t1");
        assertThat(new PersonableTraitRule().matches(aliceNode, List.of(e))).isTrue();
    }

    @Test
    void personableRule_noMatch() {
        String id = store.addNode(node("Widget"), "t1");
        MindMapNode node = store.getNode(id, "t1");
        assertThat(new PersonableTraitRule().matches(node, List.of())).isFalse();
    }

    @Test
    void projectlikeRule_matchesStatus() {
        String id = store.addNode(new NodeInput("Neocortex", subgraphId,
            null, "test", null, null,
            null, null, null, null, null,
            Map.of("status", "active")), "t1");
        MindMapNode node = store.getNode(id, "t1");

        var rule = new ProjectlikeTraitRule();
        assertThat(rule.traitName()).isEqualTo("Projectlike");
        assertThat(rule.matches(node, List.of())).isTrue();
    }

    @Test
    void organisationalRule_matchesIndustry() {
        String id = store.addNode(new NodeInput("Acme", subgraphId,
            null, "test", null, null,
            null, null, null, null, null,
            Map.of("industry", "tech")), "t1");
        MindMapNode node = store.getNode(id, "t1");

        var rule = new OrganisationalTraitRule();
        assertThat(rule.traitName()).isEqualTo("Organisational");
        assertThat(rule.matches(node, List.of())).isTrue();
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
