package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MindMapAnalyzerKCoreTest {

    private InMemoryMindMapStore store;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        subgraphId = store.createSubgraph(
            new SubgraphInput("Test", SubgraphTypes.GENERAL, null), "t1");
    }

    @Test
    void kCores_emptySubgraph_returnsEmpty() {
        List<MindMapAnalyzer.KCore> cores = MindMapAnalyzer.kCores(store, subgraphId, "t1", 2);
        assertThat(cores).isEmpty();
    }

    @Test
    void kCores_triangle_returns2Core() {
        String a = store.addNode(NodeInput.of("A", subgraphId), "t1");
        String b = store.addNode(NodeInput.of("B", subgraphId), "t1");
        String c = store.addNode(NodeInput.of("C", subgraphId), "t1");
        store.addEdge(EdgeInput.of(a, b, "knows"), "t1");
        store.addEdge(EdgeInput.of(b, c, "knows"), "t1");
        store.addEdge(EdgeInput.of(a, c, "knows"), "t1");

        List<MindMapAnalyzer.KCore> cores = MindMapAnalyzer.kCores(store, subgraphId, "t1", 2);
        assertThat(cores).hasSize(1);
        assertThat(cores.getFirst().nodeIds()).containsExactlyInAnyOrder(a, b, c);
    }

    @Test
    void kCores_isolatedNode_excluded() {
        String a = store.addNode(NodeInput.of("A", subgraphId), "t1");
        String b = store.addNode(NodeInput.of("B", subgraphId), "t1");
        String c = store.addNode(NodeInput.of("C", subgraphId), "t1");
        String d = store.addNode(NodeInput.of("D", subgraphId), "t1");
        store.addEdge(EdgeInput.of(a, b, "knows"), "t1");
        store.addEdge(EdgeInput.of(b, c, "knows"), "t1");
        store.addEdge(EdgeInput.of(a, c, "knows"), "t1");

        List<MindMapAnalyzer.KCore> cores = MindMapAnalyzer.kCores(store, subgraphId, "t1", 2);
        assertThat(cores).hasSize(1);
        assertThat(cores.getFirst().nodeIds()).doesNotContain(d);
    }

    @Test
    void kCores_twoDisconnectedTriangles_returnsTwoCores() {
        String a = store.addNode(NodeInput.of("A", subgraphId), "t1");
        String b = store.addNode(NodeInput.of("B", subgraphId), "t1");
        String c = store.addNode(NodeInput.of("C", subgraphId), "t1");
        store.addEdge(EdgeInput.of(a, b, "knows"), "t1");
        store.addEdge(EdgeInput.of(b, c, "knows"), "t1");
        store.addEdge(EdgeInput.of(a, c, "knows"), "t1");

        String d = store.addNode(NodeInput.of("D", subgraphId), "t1");
        String e = store.addNode(NodeInput.of("E", subgraphId), "t1");
        String f = store.addNode(NodeInput.of("F", subgraphId), "t1");
        store.addEdge(EdgeInput.of(d, e, "knows"), "t1");
        store.addEdge(EdgeInput.of(e, f, "knows"), "t1");
        store.addEdge(EdgeInput.of(d, f, "knows"), "t1");

        List<MindMapAnalyzer.KCore> cores = MindMapAnalyzer.kCores(store, subgraphId, "t1", 2);
        assertThat(cores).hasSize(2);
    }
}
