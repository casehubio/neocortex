package io.casehub.neocortex.knowledge.research;

import io.casehub.neocortex.knowledge.ResearchState;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchOrchestratorTest {

    private InMemoryMindMapStore mindMap;
    private ResearchSessionStore sessionStore;
    private ResearchOrchestrator orchestrator;
    private static final String TENANT = "test-tenant";

    @BeforeEach
    void setUp() {
        mindMap = new InMemoryMindMapStore();
        sessionStore = new ResearchSessionStore(":memory:");
        orchestrator = new ResearchOrchestrator(sessionStore, mindMap);
    }

    @AfterEach
    void tearDown() {
        sessionStore.close();
    }

    @Test
    void createCreatesSubgraphAndRootNode() {
        var session = orchestrator.create("Edinburgh trip",
            "{\"maxPrice\":150}", TENANT);

        assertThat(session.name()).isEqualTo("Edinburgh trip");
        assertThat(session.state()).isEqualTo(ResearchState.ACTIVE);
        assertThat(session.tenantId()).isEqualTo(TENANT);

        var subgraph = mindMap.getSubgraph(session.mindMapSubgraphId(), TENANT);
        assertThat(subgraph).isNotNull();
        assertThat(subgraph.type()).isEqualTo(SubgraphTypes.RESEARCH_AREA);
        assertThat(subgraph.rootNodeId()).isNotNull();

        var rootNode = mindMap.getNode(subgraph.rootNodeId(), TENANT);
        assertThat(rootNode.name()).isEqualTo("Edinburgh trip");
    }

    @Test
    void pauseTransitionsToCorrectState() {
        var session = orchestrator.create("Trip", null, TENANT);
        orchestrator.pause(session.id());

        var reloaded = orchestrator.get(session.id());
        assertThat(reloaded.state()).isEqualTo(ResearchState.PAUSED);
    }

    @Test
    void resumeTransitionsToActive() {
        var session = orchestrator.create("Trip", null, TENANT);
        orchestrator.pause(session.id());
        orchestrator.resume(session.id());

        var reloaded = orchestrator.get(session.id());
        assertThat(reloaded.state()).isEqualTo(ResearchState.ACTIVE);
    }

    @Test
    void completeTransitionsToCompleted() {
        var session = orchestrator.create("Trip", null, TENANT);
        orchestrator.complete(session.id());

        var reloaded = orchestrator.get(session.id());
        assertThat(reloaded.state()).isEqualTo(ResearchState.COMPLETED);
    }

    @Test
    void listActiveReturnsOnlyActiveSessions() {
        orchestrator.create("Trip 1", null, TENANT);
        var trip2 = orchestrator.create("Trip 2", null, TENANT);
        orchestrator.pause(trip2.id());
        orchestrator.create("Trip 3", null, TENANT);

        var active = orchestrator.listActive(TENANT);
        assertThat(active).hasSize(2);
        assertThat(active).extracting("name")
            .containsExactlyInAnyOrder("Trip 1", "Trip 3");
    }

    @Test
    void listActiveIsTenantIsolated() {
        orchestrator.create("Trip A", null, "t1");
        orchestrator.create("Trip B", null, "t2");

        assertThat(orchestrator.listActive("t1")).hasSize(1);
        assertThat(orchestrator.listActive("t2")).hasSize(1);
    }

    @Test
    void getReturnsNullForUnknown() {
        assertThat(orchestrator.get("nonexistent")).isNull();
    }
}
