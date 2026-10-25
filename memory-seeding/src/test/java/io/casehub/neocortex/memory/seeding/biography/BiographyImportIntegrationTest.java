package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BiographyImportIntegrationTest {

    private static final String AGENT = "test-agent";
    private static final String TENANT = "test-tenant";

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return AGENT; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @Test
    void fullRoundTrip() {
        var mindMapStore = new InMemoryMindMapStore();
        var memoryStore = new InMemoryMemoryStore(principal);

        var handlers = List.<BiographyHandler>of(
            new PlaceHandler(mindMapStore),
            new OrganisationHandler(mindMapStore, true),
            new RelationshipHandler(mindMapStore, memoryStore),
            new GoalHandler(mindMapStore),
            new LifeEventHandler(memoryStore),
            new EntityEnrichmentHandler(mindMapStore, memoryStore, true),
            new DispositionProfileHandler(mindMapStore, true)
        );
        var runner = new BiographyImportRunner(handlers);
        var loader = new BiographyLoader();

        Path biographyDir = Path.of("src/test/resources/biography-test");
        var profile = loader.loadAll(biographyDir, AGENT, TENANT);
        runner.run(profile, AGENT, TENANT);

        // Place nodes
        var placeNodes = mindMapStore.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.PLACE));
        assertThat(placeNodes).isNotEmpty();
        assertThat(placeNodes.stream().map(n -> n.name())).contains("La Casa Azul");

        // Organisation nodes
        var orgNodes = mindMapStore.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.ORGANISATION));
        assertThat(orgNodes).hasSize(1);
        assertThat(orgNodes.getFirst().name()).isEqualTo("Los Cachuchas");
        assertThat(orgNodes.getFirst().traits()).contains("Organisational");

        // Person nodes (from relationship entries)
        var personNodes = mindMapStore.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.PERSON));
        assertThat(personNodes).isNotEmpty();
        var frida = personNodes.stream()
            .filter(n -> n.name().equals("Frida Kahlo")).findFirst();
        assertThat(frida).isPresent();

        // Goal nodes with taxonomy fields
        var goalNodes = mindMapStore.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.GOAL));
        assertThat(goalNodes).isNotEmpty();
        var goal = goalNodes.stream()
            .filter(n -> n.name().equals("Establish artistic identity")).findFirst();
        assertThat(goal).isPresent();
        assertThat(goal.get().properties()).containsEntry("tier", "thematic");
        assertThat(goal.get().properties()).containsEntry("status", "active");

        // Life event memories with enrichments
        var memories = memoryStore.query(
            MemoryQuery.forSubject(Subject.of("agent", AGENT), ExperienceEvents.DOMAIN, TENANT));
        assertThat(memories).isNotEmpty();
        var polioMemory = memories.stream()
            .filter(m -> m.text().contains("polio")).findFirst();
        assertThat(polioMemory).isPresent();
        assertThat(polioMemory.get().attributes()).containsEntry("event-type", "observation");
        assertThat(polioMemory.get().attributes()).containsEntry("subject", "Frida Kahlo");
        assertThat(polioMemory.get().attributes()).containsKey(SubThoughtAttributeKeys.COUNT);
        assertThat(polioMemory.get().attributes()).containsKey(EnrichmentAttributeKeys.OCC_EMOTION_COUNT);
        assertThat(polioMemory.get().attributes()).containsKey(EnrichmentAttributeKeys.DRIVE_CURIOSITY);

        // Entity enrichments stored as node properties
        assertThat(frida.get().properties()).containsEntry("drive-baseline-curiosity", "0.85");
        assertThat(frida.get().properties()).containsEntry("drive-baseline-competence", "0.88");

        // Disposition profile stored as node properties
        assertThat(frida.get().properties()).containsEntry("disposition-socialOrient", "cooperative");
        assertThat(frida.get().properties()).containsEntry("disposition-riskAppetite", "bold");
    }
}
