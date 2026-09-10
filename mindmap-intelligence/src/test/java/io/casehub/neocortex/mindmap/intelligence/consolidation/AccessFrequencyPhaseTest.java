package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AccessFrequencyPhaseTest {

    private InMemoryMindMapStore store;
    private RetrievalAccessTracker tracker;
    private AccessFrequencyPhase phase;
    private String subgraphId;
    private String nodeId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        tracker = new RetrievalAccessTracker();
        phase = new AccessFrequencyPhase(store, tracker);
        subgraphId = store.createSubgraph(
            new SubgraphInput("Test", SubgraphTypes.GENERAL, null), "t1");
        nodeId = store.addNode(NodeInput.of("Alice", subgraphId), "t1");
    }

    @Test
    void run_flushesStorageStrength() {
        tracker.recordAccess(nodeId);
        tracker.recordAccess(nodeId);
        tracker.recordAccess(nodeId);

        phase.beginTick();
        phase.run("t1", List.of());

        MindMapNode node = store.getNode(nodeId, "t1");
        assertThat(node.property("storageStrength")).hasValue("3");
        assertThat(node.property("lastAccessed")).isPresent();
    }

    @Test
    void run_accumulatesAcrossFlushes() {
        tracker.recordAccess(nodeId);
        phase.beginTick();
        phase.run("t1", List.of());

        tracker.recordAccess(nodeId);
        tracker.recordAccess(nodeId);
        phase.beginTick();
        phase.run("t1", List.of());

        MindMapNode node = store.getNode(nodeId, "t1");
        assertThat(node.property("storageStrength")).hasValue("3");
    }

    @Test
    void run_noAccesses_noOp() {
        phase.beginTick();
        phase.run("t1", List.of());
        MindMapNode node = store.getNode(nodeId, "t1");
        assertThat(node.property("storageStrength")).isEmpty();
    }

    @Test
    void name_returnsAccessFrequency() {
        assertThat(phase.name()).isEqualTo("access-frequency");
    }
}
