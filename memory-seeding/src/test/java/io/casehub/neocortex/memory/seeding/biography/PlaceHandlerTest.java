package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceHandlerTest {

    @Test
    void createsPlaceNodeWithProvenanceAndPad() {
        var store = new InMemoryMindMapStore();
        var handler = new PlaceHandler(store);

        var entry = new PlaceEntry("casa-azul", "source.md#casa", null,
            "La Casa Azul",
            Map.of("address", "Londres 247"),
            new PlaceEntry.PadValues(0.6, 0.2, 0.5),
            List.of());

        var profile = profileWith(List.of(entry));
        handler.handle(profile, "agent-1", "tenant-1");

        var nodes = store.search(
            MindMapQuery.of("tenant-1", 10).withType(SubgraphTypes.PLACE));
        assertThat(nodes).hasSize(1);
        var node = nodes.getFirst();
        assertThat(node.name()).isEqualTo("La Casa Azul");
        assertThat(node.pleasure()).isEqualTo(0.6);
        assertThat(node.properties()).containsEntry("provenance", "biographical-import");
        assertThat(node.properties()).containsEntry("template-ref", "places.yaml#casa-azul");
        assertThat(node.properties()).containsEntry("source-ref", "source.md#casa");
        assertThat(node.properties()).containsEntry("address", "Londres 247");
    }

    @Test
    void skipsExistingPlace() {
        var store = new InMemoryMindMapStore();
        var handler = new PlaceHandler(store);

        var entry = new PlaceEntry("casa-azul", null, null,
            "La Casa Azul", Map.of(), null, List.of());
        var profile = profileWith(List.of(entry));

        handler.handle(profile, "agent-1", "tenant-1");
        handler.handle(profile, "agent-1", "tenant-1");

        var nodes = store.search(
            MindMapQuery.of("tenant-1", 10).withType(SubgraphTypes.PLACE));
        assertThat(nodes).hasSize(1);
    }

    private BiographyProfile profileWith(List<PlaceEntry> places) {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), List.of(), places, List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
