package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzyNodeResolverTest {

    private InMemoryMindMapStore store;
    private FuzzyNodeResolver resolver;
    private static final String TENANT = "test-tenant";
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        resolver = new FuzzyNodeResolver();
        subgraphId = store.createSubgraph(new SubgraphInput("person", "person", null), TENANT);
    }

    @Test
    void resolve_exactMatch_returnsNode() {
        store.addNode(NodeInput.of("Frida Kahlo", subgraphId), TENANT);
        Optional<MindMapNode> result = resolver.resolve(store, "Frida Kahlo",
            0.85, subgraphId, TENANT);
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Frida Kahlo");
    }

    @Test
    void resolve_fuzzyMatch_returnsClosest() {
        store.addNode(NodeInput.of("Frida Kahlo", subgraphId), TENANT);
        Optional<MindMapNode> result = resolver.resolve(store, "Frida Kalo",
            0.85, subgraphId, TENANT);
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Frida Kahlo");
    }

    @Test
    void resolve_noMatch_returnsEmpty() {
        store.addNode(NodeInput.of("Frida Kahlo", subgraphId), TENANT);
        Optional<MindMapNode> result = resolver.resolve(store, "Diego Rivera",
            0.85, subgraphId, TENANT);
        assertThat(result).isEmpty();
    }

    @Test
    void resolve_belowThreshold_returnsEmpty() {
        store.addNode(NodeInput.of("Frida Kahlo", subgraphId), TENANT);
        Optional<MindMapNode> result = resolver.resolve(store, "Fr",
            0.85, subgraphId, TENANT);
        assertThat(result).isEmpty();
    }

    @Test
    void resolve_nullSubgraph_searchesAllSubgraphs() {
        store.addNode(NodeInput.of("Frida Kahlo", subgraphId), TENANT);
        String conceptSg = store.createSubgraph(new SubgraphInput("concept", "concept", null), TENANT);
        store.addNode(NodeInput.of("Surrealism", conceptSg), TENANT);

        Optional<MindMapNode> result = resolver.resolve(store, "Frida Kalo",
            0.85, null, TENANT);
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Frida Kahlo");
    }

    @Test
    void resolve_multipleAboveThreshold_returnsBestMatch() {
        store.addNode(NodeInput.of("Frida Kahlo", subgraphId), TENANT);
        store.addNode(NodeInput.of("Frida Rivera", subgraphId), TENANT);

        Optional<MindMapNode> result = resolver.resolve(store, "Frida Kahlo de Rivera",
            0.70, subgraphId, TENANT);
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Frida Kahlo");
    }

    @Test
    void resolve_blankName_returnsEmpty() {
        store.addNode(NodeInput.of("Frida Kahlo", subgraphId), TENANT);
        assertThat(resolver.resolve(store, "", 0.85, subgraphId, TENANT)).isEmpty();
        assertThat(resolver.resolve(store, "   ", 0.85, subgraphId, TENANT)).isEmpty();
        assertThat(resolver.resolve(store, null, 0.85, subgraphId, TENANT)).isEmpty();
    }
}
