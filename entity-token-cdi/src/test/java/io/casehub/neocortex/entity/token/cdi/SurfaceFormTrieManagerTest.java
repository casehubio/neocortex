package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.SurfaceFormTrie;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SurfaceFormTrieManagerTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore store;
    private EntityIdentityCache identityCache;
    private SurfaceFormTrieManager trieManager;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        identityCache = new EntityIdentityCache(store);
        trieManager = new SurfaceFormTrieManager(identityCache);
    }

    @AfterEach
    void tearDown() {
        trieManager.shutdown();
    }

    @Test
    void triePicksUpEntitySurfaceForms() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        store.addNode(
            NodeInput.of("Jane Doe", sgId)
                .withProperties(Map.of("canonicalFirst", "Jane", "canonicalLast", "Doe")),
            TENANT);

        SurfaceFormTrie trie = trieManager.getTrieForTenant(TENANT);
        var matches = trie.scan("Jane Doe walked home");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).form().text()).isEqualTo("Jane Doe");
    }

    @Test
    void trieMatchesMultipleEntities() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        store.addNode(
            NodeInput.of("Jane Doe", sgId)
                .withProperties(Map.of("canonicalFirst", "Jane")),
            TENANT);
        store.addNode(
            NodeInput.of("Diego Rivera", sgId)
                .withProperties(Map.of("canonicalFirst", "Diego")),
            TENANT);

        SurfaceFormTrie trie = trieManager.getTrieForTenant(TENANT);
        var matches = trie.scan("Jane met Diego");
        assertThat(matches).hasSize(2);
    }

    @Test
    void rebuildNowRefreshesTrieAfterNewEntity() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        store.addNode(NodeInput.of("Jane Doe", sgId), TENANT);

        SurfaceFormTrie trie1 = trieManager.getTrieForTenant(TENANT);
        assertThat(trie1.scan("Diego walked")).isEmpty();

        store.addNode(
            NodeInput.of("Diego Rivera", sgId)
                .withProperties(Map.of("canonicalFirst", "Diego")),
            TENANT);
        trieManager.rebuildNow(TENANT);

        SurfaceFormTrie trie2 = trieManager.getTrieForTenant(TENANT);
        assertThat(trie2.scan("Diego walked")).hasSize(1);
    }

    @Test
    void emptyTenantReturnsEmptyTrie() {
        SurfaceFormTrie trie = trieManager.getTrieForTenant("empty-tenant");
        assertThat(trie.scan("anything")).isEmpty();
    }

    @Test
    void invalidateTenantForcesRebuild() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        store.addNode(NodeInput.of("Jane", sgId), TENANT);

        trieManager.getTrieForTenant(TENANT);
        trieManager.invalidateTenant(TENANT);
        store.addNode(
            NodeInput.of("Diego Rivera", sgId)
                .withProperties(Map.of("canonicalFirst", "Diego")),
            TENANT);

        SurfaceFormTrie trie = trieManager.getTrieForTenant(TENANT);
        assertThat(trie.scan("Diego walked")).hasSize(1);
    }
}
