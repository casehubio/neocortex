package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DispositionProfileHandlerTest {

    private static final String TENANT = "t1";

    @Test
    void storesCategoricalDispositionAxes() {
        var store = new InMemoryMindMapStore();
        var handler = new DispositionProfileHandler(store, true);

        var subgraph = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Frida Kahlo", subgraph).withProvenance("test"), TENANT);

        var entry = new DispositionProfileEntry(
            "disp-1", "Frida Kahlo",
            "cooperative", "defiant", "bold", "autonomous", "confrontational", null);

        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        var node = store.resolveNode("Frida Kahlo", subgraph, TENANT);
        assertThat(node.properties()).containsEntry("disposition-socialOrient", "cooperative");
        assertThat(node.properties()).containsEntry("disposition-ruleFollowing", "defiant");
        assertThat(node.properties()).containsEntry("disposition-riskAppetite", "bold");
        assertThat(node.properties()).containsEntry("disposition-autonomy", "autonomous");
        assertThat(node.properties()).containsEntry("disposition-conflictMode", "confrontational");
        assertThat(node.properties()).containsEntry("provenance", "biographical-import");
    }

    @Test
    void skipsNullAxes() {
        var store = new InMemoryMindMapStore();
        var handler = new DispositionProfileHandler(store, true);

        var subgraph = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Partial", subgraph).withProvenance("test"), TENANT);

        var entry = new DispositionProfileEntry(
            "disp-2", "Partial", "cooperative", null, null, null, null, null);

        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        var node = store.resolveNode("Partial", subgraph, TENANT);
        assertThat(node.properties()).containsEntry("disposition-socialOrient", "cooperative");
        assertThat(node.properties()).doesNotContainKey("disposition-ruleFollowing");
    }

    private BiographyProfile profileWith(List<DispositionProfileEntry> profiles) {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), profiles, List.of(), List.of());
    }
}
