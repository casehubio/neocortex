package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HandlerAssociationTest {

    private static final String AGENT = "test-agent";
    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore store;

    private void setupTargetNode(String name, String subgraphType) {
        store = new InMemoryMindMapStore();
        var subgraphId = BiographyUtils.ensureSubgraph(store, subgraphType, TENANT);
        store.addNode(io.casehub.neocortex.mindmap.NodeInput.of(name, subgraphId)
            .withProvenance("test"), TENANT);
    }

    @Test
    void activityHandlerProcessesAssociations() {
        setupTargetNode("Frida Kahlo", SubgraphTypes.PERSON);
        var handler = new ActivityHandler(store);

        var assoc = new Association("Frida Kahlo", "organized-by", 0.9);
        var entry = new ActivityEntry("act-1", null, "Exhibition", null, null,
            null, null, null, List.of(assoc));

        handler.handle(profileWithActivities(List.of(entry)), AGENT, TENANT);

        var activityNodes = store.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.ACTIVITY));
        assertThat(activityNodes).hasSize(1);
        var edges = store.neighbors(activityNodes.getFirst().id(), TENANT);
        assertThat(edges).anyMatch(e -> e.edgeType().equals("organized-by"));
    }

    @Test
    void projectHandlerProcessesAssociations() {
        setupTargetNode("Frida Kahlo", SubgraphTypes.PERSON);
        var handler = new ProjectHandler(store);

        var assoc = new Association("Frida Kahlo", "created-by", 0.8);
        var entry = new ProjectEntry("proj-1", null, "Las dos Fridas",
            "A painting", null, null, List.of(assoc));

        handler.handle(profileWithProjects(List.of(entry)), AGENT, TENANT);

        var projectNodes = store.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.PROJECT));
        assertThat(projectNodes).hasSize(1);
        var edges = store.neighbors(projectNodes.getFirst().id(), TENANT);
        assertThat(edges).anyMatch(e -> e.edgeType().equals("created-by"));
    }

    @Test
    void culturalContextHandlerProcessesAssociations() {
        setupTargetNode("Mexico", SubgraphTypes.PLACE);
        var handler = new CulturalContextHandler(store);

        var assoc = new Association("Mexico", "originates-from", null);
        var entry = new CulturalContextEntry("ctx-1", null,
            "Post-revolutionary Mexican identity", null, null, List.of(assoc));

        handler.handle(profileWithCulturalContexts(List.of(entry)), AGENT, TENANT);

        var culturalNodes = store.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.CULTURAL));
        assertThat(culturalNodes).hasSize(1);
        var edges = store.neighbors(culturalNodes.getFirst().id(), TENANT);
        assertThat(edges).anyMatch(e -> e.edgeType().equals("originates-from"));
    }

    @Test
    void beliefHandlerProcessesAssociations() {
        setupTargetNode("Frida Kahlo", SubgraphTypes.PERSON);
        var handler = new BeliefHandler(store);

        var assoc = new Association("Frida Kahlo", "held-by", 0.7);
        var entry = new BeliefEntry("belief-1", null, null, null,
            "Pain as artistic fuel", null, null, null, List.of(assoc));

        handler.handle(profileWithBeliefs(List.of(entry)), AGENT, TENANT);

        var beliefNodes = store.search(
            MindMapQuery.of(TENANT, 10).withType(SubgraphTypes.COGNITIVE));
        assertThat(beliefNodes).hasSize(1);
        var edges = store.neighbors(beliefNodes.getFirst().id(), TENANT);
        assertThat(edges).anyMatch(e -> e.edgeType().equals("held-by"));
    }

    private BiographyProfile profileWithActivities(List<ActivityEntry> entries) {
        return new BiographyProfile(AGENT, TENANT,
            List.of(), List.of(), List.of(), List.of(), entries,
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());
    }

    private BiographyProfile profileWithProjects(List<ProjectEntry> entries) {
        return new BiographyProfile(AGENT, TENANT,
            List.of(), List.of(), List.of(), List.of(), List.of(),
            entries, List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());
    }

    private BiographyProfile profileWithCulturalContexts(List<CulturalContextEntry> entries) {
        return new BiographyProfile(AGENT, TENANT,
            entries, List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());
    }

    private BiographyProfile profileWithBeliefs(List<BeliefEntry> entries) {
        return new BiographyProfile(AGENT, TENANT,
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), entries,
            List.of(), List.of(), List.of(), List.of());
    }
}
