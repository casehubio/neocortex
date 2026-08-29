package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraitProxyTest {

    private InMemoryMindMapStore store;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        subgraphId = store.createSubgraph(
            new SubgraphInput("Test", SubgraphType.GENERAL, null), "t1");
    }

    @Test
    void as_returnsProxyImplementingInterface() {
        String id = store.addNode(new NodeInput("Alice", subgraphId,
            null, "test", null, null,
            null, null, null, null, null,
            Map.of("birthday", "1990-01-15", "role", "engineer")), "t1");
        MindMapNode node = store.getNode(id, "t1");

        Personable p = TraitProxy.as(node, Personable.class);
        assertThat(p).isInstanceOf(Personable.class);
        assertThat(p.birthday()).isEqualTo(Optional.of("1990-01-15"));
        assertThat(p.role()).isEqualTo(Optional.of("engineer"));
    }

    @Test
    void as_missingProperty_returnsEmpty() {
        String id = store.addNode(new NodeInput("Alice", subgraphId,
            null, "test", null, null,
            null, null, null, null, null, null), "t1");
        MindMapNode node = store.getNode(id, "t1");

        Personable p = TraitProxy.as(node, Personable.class);
        assertThat(p.birthday()).isEmpty();
        assertThat(p.email()).isEmpty();
    }

    @Test
    void as_nonInterface_throwsIllegalArgument() {
        String id = store.addNode(new NodeInput("Alice", subgraphId,
            null, "test", null, null,
            null, null, null, null, null, null), "t1");
        MindMapNode node = store.getNode(id, "t1");

        assertThatThrownBy(() -> TraitProxy.as(node, String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("interface");
    }

    @Test
    void as_projectlike_readsProperties() {
        String id = store.addNode(new NodeInput("Neocortex", subgraphId,
            null, "test", null, null,
            null, null, null, null, null,
            Map.of("status", "active", "startDate", "2026-01-01")), "t1");
        MindMapNode node = store.getNode(id, "t1");

        Projectlike p = TraitProxy.as(node, Projectlike.class);
        assertThat(p.status()).isEqualTo(Optional.of("active"));
        assertThat(p.startDate()).isEqualTo(Optional.of("2026-01-01"));
        assertThat(p.endDate()).isEmpty();
    }

    @Test
    void as_toString_includesNodeName() {
        String id = store.addNode(new NodeInput("Alice", subgraphId,
            null, "test", null, null,
            null, null, null, null, null, null), "t1");
        MindMapNode node = store.getNode(id, "t1");

        Personable p = TraitProxy.as(node, Personable.class);
        assertThat(p.toString()).contains("Alice").contains("Personable");
    }

    @Test
    void as_equals_sameNodeSameInterface_areEqual() {
        String id = store.addNode(new NodeInput("Alice", subgraphId,
            null, "test", null, null,
            null, null, null, null, null, null), "t1");
        MindMapNode node = store.getNode(id, "t1");

        Personable p1 = TraitProxy.as(node, Personable.class);
        Personable p2 = TraitProxy.as(node, Personable.class);
        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }
}
