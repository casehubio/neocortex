package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RelationshipHandlerTest {

    private static final String TENANT = "t1";

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "agent-1"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @Test
    void createsPersonNodeWithBdiAndAffect() {
        var store = new InMemoryMindMapStore();
        var memStore = new InMemoryMemoryStore(principal);
        var handler = new RelationshipHandler(store, memStore);

        var entry = new RelationshipEntry("diego-rivera", null, null,
            "Diego Rivera", List.of("Personable"),
            new RelationshipEntry.BdiValues("Brilliant artist", "Mutual respect", "Maintain partnership"),
            new RelationshipEntry.AffectValues(new RelationshipEntry.PadValues(0.2, 0.7, -0.3)),
            new RelationshipEntry.DynamicsValues(0.4, "confrontational"),
            Map.of("relationship_type", "spouse"));

        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        var nodes = store.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.PERSON));
        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().name()).isEqualTo("Diego Rivera");
        assertThat(nodes.getFirst().traits()).contains("Personable");
        assertThat(nodes.getFirst().pleasure()).isEqualTo(0.2);
        assertThat(nodes.getFirst().properties()).containsEntry("trust", "0.4");
    }

    private BiographyProfile profileWith(List<RelationshipEntry> rels) {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), rels, List.of(), List.of(), List.of());
    }
}
