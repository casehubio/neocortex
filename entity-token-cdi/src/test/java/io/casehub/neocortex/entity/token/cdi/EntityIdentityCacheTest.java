package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityIdentity;
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

    @Test
    void readsAliasesFromNodeProperties() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        String nodeId = store.addNode(
                NodeInput.of("David Bowie", sgId)
                         .withProperties(Map.of(
                                 "canonicalFirst", "David",
                                 "aliases", "David Robert Jones,Davy Jones")),
                TENANT);

        EntityIdentity identity = cache.getIdentity(nodeId, TENANT);
        assertThat(identity.aliases()).containsExactlyInAnyOrder("David Robert Jones", "Davy Jones");
    }

    @Test
    void emptyAliasPropertyReturnsEmptySet() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        String nodeId = store.addNode(
                NodeInput.of("Jane Doe", sgId)
                         .withProperties(Map.of("canonicalFirst", "Jane")),
                TENANT);

        EntityIdentity identity = cache.getIdentity(nodeId, TENANT);
        assertThat(identity.aliases()).isEmpty();
    }

    @Test
    void discoversAlterEgosViaPersonaOfEdge() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        String bowieId = store.addNode(
                NodeInput.of("David Bowie", sgId)
                         .withProperties(Map.of("canonicalFirst", "David", "canonicalLast", "Bowie")),
                TENANT);
        String ziggyId = store.addNode(
                NodeInput.of("Ziggy Stardust", sgId)
                         .withProperties(Map.of("canonicalFirst", "Ziggy", "preferredShort", "Ziggy", "personaQualifier", "ziggy")),
                TENANT);
        store.addEdge(io.casehub.neocortex.mindmap.EdgeInput.of(ziggyId, bowieId, "persona-of"), TENANT);

        EntityIdentity bowie = cache.getIdentity(bowieId, TENANT);
        assertThat(bowie).isNotNull();
        assertThat(bowie.alterEgos()).containsKey("ziggy");
        EntityIdentity ziggyEgo = bowie.alterEgos().get("ziggy");
        assertThat(ziggyEgo.entityId()).isEqualTo(bowieId + ".ziggy");
        assertThat(ziggyEgo.canonicalFull()).isEqualTo("Ziggy Stardust");
        assertThat(ziggyEgo.nameParts()).containsEntry("canonicalFirst", "Ziggy");
    }

    @Test
    void entityWithNoPersonasHasEmptyAlterEgos() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        store.addNode(NodeInput.of("Jane Doe", sgId).withProperties(Map.of("canonicalFirst", "Jane")), TENANT);
        var all = cache.getAllIdentities(TENANT);
        assertThat(all.values()).allMatch(id -> id.alterEgos().isEmpty());
    }

    @Test
    void personaNodeNotIncludedAsTopLevelEntity() {
        String sgId    = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        String bowieId = store.addNode(NodeInput.of("David Bowie", sgId), TENANT);
        String ziggyId = store.addNode(NodeInput.of("Ziggy Stardust", sgId)
                                                .withProperties(Map.of("personaQualifier", "ziggy")), TENANT);
        store.addEdge(io.casehub.neocortex.mindmap.EdgeInput.of(ziggyId, bowieId, "persona-of"), TENANT);
        assertThat(cache.getAllIdentities(TENANT)).doesNotContainKey(ziggyId);
        assertThat(cache.getAllIdentities(TENANT)).containsKey(bowieId);
    }
}
