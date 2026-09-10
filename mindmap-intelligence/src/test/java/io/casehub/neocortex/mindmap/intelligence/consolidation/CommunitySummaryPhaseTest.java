package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CommunitySummaryPhaseTest {

    private InMemoryMindMapStore store;
    private CommunitySummaryPhase phase;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        phase = new CommunitySummaryPhase(store, null, 2, 3, 5);
        subgraphId = store.createSubgraph(
            new SubgraphInput("People", SubgraphTypes.PERSON, null), "t1");
    }

    private void createTriangle(String name1, String name2, String name3) {
        String a = store.addNode(NodeInput.of(name1, subgraphId), "t1");
        String b = store.addNode(NodeInput.of(name2, subgraphId), "t1");
        String c = store.addNode(NodeInput.of(name3, subgraphId), "t1");
        store.addEdge(EdgeInput.of(a, b, "knows"), "t1");
        store.addEdge(EdgeInput.of(b, c, "knows"), "t1");
        store.addEdge(EdgeInput.of(a, c, "knows"), "t1");
    }

    @Test
    void run_noLlm_createsSummaryWithDefaultText() {
        createTriangle("Alice", "Bob", "Carol");

        phase.run("t1", List.of());

        var nodes = store.nodesIn(subgraphId, "t1");
        var summaryNodes = nodes.stream()
            .filter(n -> n.traits().contains("Summary"))
            .toList();
        assertThat(summaryNodes).hasSize(1);
        assertThat(summaryNodes.getFirst().property("coreHash")).isPresent();
        assertThat(summaryNodes.getFirst().property("memberCount")).hasValue("3");
    }

    @Test
    void run_unchangedCluster_skipsSummary() {
        createTriangle("Alice", "Bob", "Carol");

        phase.run("t1", List.of());
        phase.run("t1", List.of());

        var summaryNodes = store.nodesIn(subgraphId, "t1").stream()
            .filter(n -> n.traits().contains("Summary"))
            .toList();
        assertThat(summaryNodes).hasSize(1);
    }

    @Test
    void run_dissolvedCluster_erasesStaleSummary() {
        String a = store.addNode(NodeInput.of("Alice", subgraphId), "t1");
        String b = store.addNode(NodeInput.of("Bob", subgraphId), "t1");
        String c = store.addNode(NodeInput.of("Carol", subgraphId), "t1");
        store.addEdge(EdgeInput.of(a, b, "knows"), "t1");
        store.addEdge(EdgeInput.of(b, c, "knows"), "t1");
        store.addEdge(EdgeInput.of(a, c, "knows"), "t1");

        phase.run("t1", List.of());
        assertThat(store.nodesIn(subgraphId, "t1").stream()
            .filter(n -> n.traits().contains("Summary")).count()).isEqualTo(1);

        store.eraseNode(c, "t1");

        phase.run("t1", List.of());
        assertThat(store.nodesIn(subgraphId, "t1").stream()
            .filter(n -> n.traits().contains("Summary")).count()).isEqualTo(0);
    }

    @Test
    void name_returnsCommunitySummary() {
        assertThat(phase.name()).isEqualTo("community-summary");
    }
}
