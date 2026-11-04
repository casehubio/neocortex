package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityIdentity;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntityIdentityCacheTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore store;
    private EntityIdentityCache cache;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        cache = new EntityIdentityCache(store);
    }

    @Test
    void populatesFromMindMapStore() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        String nodeId = store.addNode(
            NodeInput.of("Jane Doe", sgId)
                .withProperties(Map.of(
                    "canonicalFirst", "Jane",
                    "canonicalLast", "Doe",
                    "pronounsSubject", "she")),
            TENANT);

        EntityIdentity identity = cache.getIdentity(nodeId, TENANT);
        assertThat(identity).isNotNull();
        assertThat(identity.canonicalFull()).isEqualTo("Jane Doe");
        assertThat(identity.nameParts()).containsEntry("canonicalFirst", "Jane");
        assertThat(identity.nameParts()).containsEntry("canonicalLast", "Doe");
        assertThat(identity.pronouns()).containsEntry("pronounsSubject", "she");
    }

    @Test
    void usesNodeNameAsFallbackCanonicalFull() {
        String sgId = store.createSubgraph(new SubgraphInput("Places", SubgraphTypes.PLACE, null), TENANT);
        String nodeId = store.addNode(NodeInput.of("Mexico City", sgId), TENANT);

        EntityIdentity identity = cache.getIdentity(nodeId, TENANT);
        assertThat(identity).isNotNull();
        assertThat(identity.canonicalFull()).isEqualTo("Mexico City");
    }

    @Test
    void ignoresNonEntitySubgraphs() {
        String sgId = store.createSubgraph(new SubgraphInput("Goals", "goal", null), TENANT);
        store.addNode(NodeInput.of("Learn Spanish", sgId), TENANT);

        assertThat(cache.getAllIdentities(TENANT)).isEmpty();
    }

    @Test
    void invalidateRefreshesEntity() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        String nodeId = store.addNode(
            NodeInput.of("Jane Doe", sgId)
                .withProperties(Map.of("canonicalFirst", "Jane")),
            TENANT);

        assertThat(cache.getIdentity(nodeId, TENANT).nameParts().get("canonicalFirst"))
            .isEqualTo("Jane");

        store.updateNode(nodeId,
            io.casehub.neocortex.mindmap.NodeUpdate.empty()
                .withPropertiesToSet(Map.of("canonicalFirst", "Janet")),
            TENANT);

        cache.invalidate(nodeId, TENANT);
        assertThat(cache.getIdentity(nodeId, TENANT).nameParts().get("canonicalFirst"))
            .isEqualTo("Janet");
    }

    @Test
    void invalidateTenantClearsCache() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        store.addNode(NodeInput.of("Jane Doe", sgId), TENANT);

        assertThat(cache.getAllIdentities(TENANT)).isNotEmpty();
        cache.invalidateTenant(TENANT);
        assertThat(cache.getAllIdentities(TENANT)).isNotEmpty();
    }

    @Test
    void surfaceFormsFromIdentity() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        String nodeId = store.addNode(
            NodeInput.of("Jane Doe", sgId)
                .withProperties(Map.of(
                    "canonicalFirst", "Jane",
                    "canonicalLast", "Doe",
                    "preferredShort", "Janey")),
            TENANT);

        EntityIdentity identity = cache.getIdentity(nodeId, TENANT);
        var forms = identity.surfaceForms();
        assertThat(forms).extracting(f -> f.text())
            .contains("Jane Doe", "Jane", "Doe", "Janey");
    }
}
