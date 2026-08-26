package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.memory.MemoryEntityErased;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCasesErased;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NodeRefCleanupObserverTest {

    private InMemoryMindMapStore store;
    private NodeRefCleanupObserver observer;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        observer = new NodeRefCleanupObserver(store);
        subgraphId = store.createSubgraph(
            new SubgraphInput("Test", SubgraphType.GENERAL, null), "t1");
    }

    @Test
    void memoryEntityErased_removesMatchingNodeRefs() {
        NodeRef memRef = new NodeRef("memory", "mem-123", null);
        NodeRef otherRef = new NodeRef("external", "ext-1", null);
        String nodeId = store.addNode(new NodeInput("Alice", subgraphId,
            ConfidenceOrigin.STATED, null, "test", null,
            Set.of(memRef, otherRef), null, null, null, null, null, null), "t1");

        observer.onMemoryEntityErased(new MemoryEntityErased.ByEntity(
            "t1", 3, "mem-123", Instant.now()));

        MindMapNode node = store.getNode(nodeId, "t1");
        assertThat(node.refs()).containsExactly(otherRef);
    }

    @Test
    void cbrCasesErased_removesMatchingNodeRefs() {
        NodeRef cbrRef = new NodeRef("cbr", "cbr-456", null);
        String nodeId = store.addNode(new NodeInput("Bob", subgraphId,
            ConfidenceOrigin.STATED, null, "test", null,
            Set.of(cbrRef), null, null, null, null, null, null), "t1");

        observer.onCbrCasesErased(new CbrCasesErased.ByEntity(
            "t1", 2, "cbr-456", Instant.now()));

        MindMapNode node = store.getNode(nodeId, "t1");
        assertThat(node.refs()).isEmpty();
    }

    @Test
    void noMatchingRefs_noChanges() {
        NodeRef unrelated = new NodeRef("external", "ext-1", null);
        String nodeId = store.addNode(new NodeInput("Carol", subgraphId,
            ConfidenceOrigin.STATED, null, "test", null,
            Set.of(unrelated), null, null, null, null, null, null), "t1");

        observer.onMemoryEntityErased(new MemoryEntityErased.ByEntity(
            "t1", 1, "nonexistent", Instant.now()));

        MindMapNode node = store.getNode(nodeId, "t1");
        assertThat(node.refs()).containsExactly(unrelated);
    }

    @Test
    void multipleNodes_onlyAffectedRefsRemoved() {
        NodeRef memRef = new NodeRef("memory", "mem-123", null);
        NodeRef otherMemRef = new NodeRef("memory", "mem-999", null);

        String alice = store.addNode(new NodeInput("Alice", subgraphId,
            ConfidenceOrigin.STATED, null, "test", null,
            Set.of(memRef), null, null, null, null, null, null), "t1");
        String bob = store.addNode(new NodeInput("Bob", subgraphId,
            ConfidenceOrigin.STATED, null, "test", null,
            Set.of(otherMemRef), null, null, null, null, null, null), "t1");

        observer.onMemoryEntityErased(new MemoryEntityErased.ByEntity(
            "t1", 1, "mem-123", Instant.now()));

        assertThat(store.getNode(alice, "t1").refs()).isEmpty();
        assertThat(store.getNode(bob, "t1").refs()).containsExactly(otherMemRef);
    }

    @Test
    void qualifierMatching_removesAllMatchingIds() {
        NodeRef ref1 = new NodeRef("memory", "mem-123", "domain-a");
        NodeRef ref2 = new NodeRef("memory", "mem-123", "domain-b");
        String nodeId = store.addNode(new NodeInput("Dave", subgraphId,
            ConfidenceOrigin.STATED, null, "test", null,
            Set.of(ref1, ref2), null, null, null, null, null, null), "t1");

        observer.onMemoryEntityErased(new MemoryEntityErased.ByEntity(
            "t1", 1, "mem-123", Instant.now()));

        MindMapNode node = store.getNode(nodeId, "t1");
        assertThat(node.refs()).isEmpty();
    }
}
