package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntityEnrichmentHandlerTest {

    private static final String TENANT = "t1";

    @Test
    void storesDriveBaselinesOnEntityNode() {
        var store = new InMemoryMindMapStore();
        var handler = new EntityEnrichmentHandler(store, true);

        var subgraph = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Frida Kahlo", subgraph).withProvenance("test"), TENANT);

        var baselines = Map.of(
            "curiosity", new EntityEnrichmentEntry.DriveBaseline(0.85, "Strong exploration"),
            "competence", new EntityEnrichmentEntry.DriveBaseline(0.88, "High skill drive"));
        var entry = new EntityEnrichmentEntry("enrich-1", "Frida Kahlo", baselines, null, null, null);

        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        var node = store.resolveNode("Frida Kahlo", subgraph, TENANT);
        assertThat(node.properties()).containsEntry("drive-baseline-curiosity", "0.85");
        assertThat(node.properties()).containsEntry("drive-baseline-curiosity-justification", "Strong exploration");
        assertThat(node.properties()).containsEntry("drive-baseline-competence", "0.88");
    }

    @Test
    void storesCapsPatterns() {
        var store = new InMemoryMindMapStore();
        var handler = new EntityEnrichmentHandler(store, true);

        var subgraph = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Test Entity", subgraph).withProvenance("test"), TENANT);

        var patterns = List.of(
            new EntityEnrichmentEntry.CapsPattern("threat -> creative", 0.8));
        var entry = new EntityEnrichmentEntry("enrich-2", "Test Entity", null, patterns, null, null);

        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);

        var node = store.resolveNode("Test Entity", subgraph, TENANT);
        assertThat(node.properties()).containsEntry("caps-pattern-count", "1");
        assertThat(node.properties()).containsEntry("caps-pattern-0-pattern", "threat -> creative");
        assertThat(node.properties()).containsEntry("caps-pattern-0-frequency", "0.8");
    }

    @Test
    void skipsUnresolvedEntity() {
        var store = new InMemoryMindMapStore();
        var handler = new EntityEnrichmentHandler(store, true);

        var entry = new EntityEnrichmentEntry("enrich-3", "Unknown Entity",
            Map.of("curiosity", new EntityEnrichmentEntry.DriveBaseline(0.5, null)),
            null, null, null);

        handler.handle(profileWith(List.of(entry)), "agent-1", TENANT);
        // No exception, no crash — silently skips
    }

    private BiographyProfile profileWith(List<EntityEnrichmentEntry> enrichments) {
        return new BiographyProfile("a1", "t1",
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            enrichments, List.of(), List.of());
    }
}
