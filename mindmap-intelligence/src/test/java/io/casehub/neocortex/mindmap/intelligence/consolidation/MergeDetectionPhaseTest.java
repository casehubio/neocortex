package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class MergeDetectionPhaseTest {

    private InMemoryMindMapStore store;
    private MergeDetectionPhase phase;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        phase = new MergeDetectionPhase(store, null, 0.85, 0.3, 0.9, 10);
        subgraphId = store.createSubgraph(
            new SubgraphInput("People", SubgraphTypes.PERSON, null), "t1");
    }

    @Test
    void detectCandidates_similarNamesWithSharedNeighbor_returnsCandidate() {
        String id1 = store.addNode(NodeInput.of("Alice Smith", subgraphId), "t1");
        String id2 = store.addNode(NodeInput.of("Alice Smyth", subgraphId), "t1");
        String shared = store.addNode(NodeInput.of("Project X", subgraphId), "t1");
        store.addEdge(EdgeInput.of(id1, shared, "works-on"), "t1");
        store.addEdge(EdgeInput.of(id2, shared, "works-on"), "t1");

        List<MergeCandidate> candidates = phase.detectCandidates(subgraphId, "t1");
        assertThat(candidates).isNotEmpty();
        assertThat(candidates.getFirst().score()).isGreaterThan(0.7);
        assertThat(candidates.getFirst().reason()).isEqualTo("name+neighbors");
    }

    @Test
    void detectCandidates_differentNames_noCandidates() {
        store.addNode(NodeInput.of("Alice Smith", subgraphId), "t1");
        store.addNode(NodeInput.of("Bob Jones", subgraphId), "t1");

        List<MergeCandidate> candidates = phase.detectCandidates(subgraphId, "t1");
        assertThat(candidates).isEmpty();
    }

    @Test
    void run_autoMergesHighConfidence() {
        String id1 = store.addNode(NodeInput.of("Alice Smith", subgraphId)
            .withProperties(Map.of("storageStrength", "10")), "t1");
        String id2 = store.addNode(NodeInput.of("Alice Smith", subgraphId)
            .withProperties(Map.of("storageStrength", "5")), "t1");
        String sharedNeighbor = store.addNode(NodeInput.of("Project X", subgraphId), "t1");
        store.addEdge(EdgeInput.of(id1, sharedNeighbor, "works-on"), "t1");
        store.addEdge(EdgeInput.of(id2, sharedNeighbor, "works-on"), "t1");

        phase.run("t1", List.of());

        var nodes = store.nodesIn(subgraphId, "t1");
        long aliceCount = nodes.stream().filter(n -> n.name().equals("Alice Smith")).count();
        assertThat(aliceCount).isEqualTo(1);
    }

    @Test
    void run_excludesSummaryTraitNodes() {
        store.addNode(NodeInput.of("Summary of cluster", subgraphId)
            .withTraits(Set.of("Summary")), "t1");
        store.addNode(NodeInput.of("Summary of cluster 2", subgraphId)
            .withTraits(Set.of("Summary")), "t1");

        List<MergeCandidate> candidates = phase.detectCandidates(subgraphId, "t1");
        assertThat(candidates).isEmpty();
    }

    @Test
    void name_returnsMergeDetection() {
        assertThat(phase.name()).isEqualTo("merge-detection");
    }
}
