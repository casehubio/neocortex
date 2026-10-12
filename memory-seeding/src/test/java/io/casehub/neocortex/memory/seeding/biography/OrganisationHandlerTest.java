package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrganisationHandlerTest {

    @Test
    void createsOrganisationNodeWithTraitsAndProperties() {
        var store = new InMemoryMindMapStore();
        var handler = new OrganisationHandler(store, true);

        var entry = new OrganisationEntry(
            "org-cachuchas", null, "STATED", "Los Cachuchas",
            List.of("Organisational"),
            Map.of("type", "student group", "city", "Mexico City"),
            new PadValues(0.7, 0.6, 0.5),
            null, "1920~", null);

        handler.handle(profileWith(List.of(entry)), "agent-1", "tenant-1");

        var nodes = store.search(
            MindMapQuery.of("tenant-1", 10).withType(SubgraphTypes.ORGANISATION));
        assertThat(nodes).hasSize(1);
        var node = nodes.getFirst();
        assertThat(node.name()).isEqualTo("Los Cachuchas");
        assertThat(node.traits()).contains("Organisational");
        assertThat(node.properties()).containsEntry("type", "student group");
        assertThat(node.properties()).containsEntry("city", "Mexico City");
        assertThat(node.properties()).containsEntry("valid-from", "1920~");
        assertThat(node.pleasure()).isEqualTo(0.7);
    }

    @Test
    void skipsExistingOrganisation() {
        var store = new InMemoryMindMapStore();
        var handler = new OrganisationHandler(store, true);

        var entry = new OrganisationEntry(
            "org-1", null, null, "Test Org",
            null, null, null, null, null, null);

        var profile = profileWith(List.of(entry));
        handler.handle(profile, "agent-1", "tenant-1");
        handler.handle(profile, "agent-1", "tenant-1");

        var nodes = store.search(
            MindMapQuery.of("tenant-1", 10).withType(SubgraphTypes.ORGANISATION));
        assertThat(nodes).hasSize(1);
    }

    private BiographyProfile profileWith(List<OrganisationEntry> orgs) {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), orgs, List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of());
    }
}
