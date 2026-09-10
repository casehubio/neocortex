package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class MindMapStoreIdleTrackerTest {

    private InMemoryMindMapStore delegate;
    private IdleTracker idleTracker;
    private MindMapStoreIdleTracker decorator;

    @BeforeEach
    void setUp() {
        delegate = new InMemoryMindMapStore();
        idleTracker = new IdleTracker();
        decorator = new MindMapStoreIdleTracker(delegate, idleTracker);
    }

    @Test
    void addNode_recordsWrite() {
        assertThat(idleTracker.isIdle(Duration.ofSeconds(1))).isTrue();
        String sgId = decorator.createSubgraph(
            new SubgraphInput("Test", SubgraphTypes.GENERAL, null), "t1");
        decorator.addNode(NodeInput.of("Alice", sgId), "t1");
        assertThat(idleTracker.isIdle(Duration.ofSeconds(1))).isFalse();
    }

    @Test
    void getNode_doesNotRecordWrite() {
        String sgId = decorator.createSubgraph(
            new SubgraphInput("Test", SubgraphTypes.GENERAL, null), "t1");
        String nodeId = decorator.addNode(NodeInput.of("Alice", sgId), "t1");
        idleTracker.recordWriteAt(Instant.EPOCH);
        decorator.getNode(nodeId, "t1");
        assertThat(idleTracker.isIdle(Duration.ofSeconds(1))).isTrue();
    }

    @Test
    void search_doesNotRecordWrite() {
        idleTracker.recordWriteAt(Instant.EPOCH);
        decorator.search(MindMapQuery.of("t1", 10));
        assertThat(idleTracker.isIdle(Duration.ofSeconds(1))).isTrue();
    }

    @Test
    void addEdge_recordsWrite() {
        String sgId = decorator.createSubgraph(
            new SubgraphInput("Test", SubgraphTypes.GENERAL, null), "t1");
        String n1 = decorator.addNode(NodeInput.of("Alice", sgId), "t1");
        String n2 = decorator.addNode(NodeInput.of("Bob", sgId), "t1");
        idleTracker.recordWriteAt(Instant.EPOCH);
        decorator.addEdge(EdgeInput.of(n1, n2, "knows"), "t1");
        assertThat(idleTracker.isIdle(Duration.ofSeconds(1))).isFalse();
    }

    @Test
    void mergeNodes_recordsWrite() {
        String sgId = decorator.createSubgraph(
            new SubgraphInput("Test", SubgraphTypes.GENERAL, null), "t1");
        String n1 = decorator.addNode(NodeInput.of("Alice", sgId), "t1");
        String n2 = decorator.addNode(NodeInput.of("Alice2", sgId), "t1");
        idleTracker.recordWriteAt(Instant.EPOCH);
        decorator.mergeNodes(n1, n2, "t1");
        assertThat(idleTracker.isIdle(Duration.ofSeconds(1))).isFalse();
    }
}
