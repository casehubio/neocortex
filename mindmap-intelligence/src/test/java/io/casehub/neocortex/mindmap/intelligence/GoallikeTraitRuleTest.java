package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoallikeTraitRuleTest {

    private InMemoryMindMapStore store;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        subgraphId = store.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), "t1");
    }

    @Test
    void traitName() {
        assertThat(new GoallikeTraitRule().traitName()).isEqualTo("Goallike");
    }

    @Test
    void matchesNodeWithDescriptionAndStatus() {
        String id = store.addNode(new NodeInput("Find diamond", subgraphId,
                null, "test", null, null,
                null, null, null, null, null,
                Map.of("description", "find diamond", "status", "active")), "t1");
        MindMapNode node = store.getNode(id, "t1");
        assertThat(new GoallikeTraitRule().matches(node, List.of())).isTrue();
    }

    @Test
    void matchesNodeWithDescriptionOnly() {
        String id = store.addNode(new NodeInput("Explore topic", subgraphId,
                null, "test", null, null,
                null, null, null, null, null,
                Map.of("description", "explore topic")), "t1");
        MindMapNode node = store.getNode(id, "t1");
        assertThat(new GoallikeTraitRule().matches(node, List.of())).isTrue();
    }

    @Test
    void doesNotMatchWithoutDescriptionOrStatus() {
        String id = store.addNode(new NodeInput("Alice", subgraphId,
                null, "test", null, null,
                null, null, null, null, null,
                Map.of("name", "Alice")), "t1");
        MindMapNode node = store.getNode(id, "t1");
        assertThat(new GoallikeTraitRule().matches(node, List.of())).isFalse();
    }

    @Test
    void matchesNodeWithGoalSubgraphProperties() {
        String id = store.addNode(new NodeInput("Learn ML", subgraphId,
                null, "test", null, null,
                null, null, null, null, null,
                Map.of("description", "learn ML", "horizon", "long", "urgency", "0.5")), "t1");
        MindMapNode node = store.getNode(id, "t1");
        assertThat(new GoallikeTraitRule().matches(node, List.of())).isTrue();
    }

    @Test
    void doesNotMatchStatusAlone() {
        String id = store.addNode(new NodeInput("Active Thing", subgraphId,
                null, "test", null, null,
                null, null, null, null, null,
                Map.of("status", "active")), "t1");
        MindMapNode node = store.getNode(id, "t1");
        assertThat(new GoallikeTraitRule().matches(node, List.of())).isFalse();
    }
}
