package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.platform.api.identity.CurrentPrincipal;

import java.util.Set;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SurfacingAggregationPhaseTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private SurfacingAggregationPhase phase;
    private String goalSubgraphId;
    private static final String TENANT = "t1";
    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        CurrentPrincipal principal = new CurrentPrincipal() {
            @Override public String actorId() { return "actor"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return true; }
        };
        memoryStore = new InMemoryMemoryStore(principal);
        phase = new SurfacingAggregationPhase(mindMapStore, memoryStore);
        goalSubgraphId = mindMapStore.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    @Test
    void phaseName() {
        assertThat(phase.name()).isEqualTo("surfacing-aggregation");
    }

    @Test
    void noSurfacingEvents_noNodeUpdates() {
        String goalId = createGoal("Buy gift");
        phase.run(TENANT, List.of());
        MindMapNode node = mindMapStore.getNode(goalId, TENANT);
        assertThat(node.property("surfaced-count")).isEmpty();
    }

    @Test
    void oneSurfacingEvent_setsCountAndTimestamps() {
        String goalId = createGoal("Buy gift");
        recordSurfacing(goalId, "agent-1");

        phase.run(TENANT, List.of());

        MindMapNode node = mindMapStore.getNode(goalId, TENANT);
        assertThat(node.property("surfaced-count")).hasValue("1");
        assertThat(node.property("first-surfaced-at")).isPresent();
        assertThat(node.property("last-surfaced-at")).isPresent();
    }

    @Test
    void multipleSurfacingEvents_incrementsCount() {
        String goalId = createGoal("Buy gift");
        recordSurfacing(goalId, "agent-1");
        recordSurfacing(goalId, "agent-1");
        recordSurfacing(goalId, "agent-1");

        phase.run(TENANT, List.of());

        MindMapNode node = mindMapStore.getNode(goalId, TENANT);
        assertThat(node.property("surfaced-count")).hasValue("3");
    }

    @Test
    void multipleGoals_trackedIndependently() {
        String goal1 = createGoal("Buy gift");
        String goal2 = createGoal("Send card");
        recordSurfacing(goal1, "agent-1");
        recordSurfacing(goal1, "agent-1");
        recordSurfacing(goal2, "agent-1");

        phase.run(TENANT, List.of());

        assertThat(mindMapStore.getNode(goal1, TENANT)
                .property("surfaced-count")).hasValue("2");
        assertThat(mindMapStore.getNode(goal2, TENANT)
                .property("surfaced-count")).hasValue("1");
    }

    @Test
    void progressEvent_setsLastProgressAt() {
        String goalId = createGoal("Buy gift");
        recordSurfacing(goalId, "agent-1");
        recordProgress(goalId, "agent-1");

        phase.run(TENANT, List.of());

        MindMapNode node = mindMapStore.getNode(goalId, TENANT);
        assertThat(node.property("surfaced-count")).hasValue("1");
        assertThat(node.property("last-progress-at")).isPresent();
        assertThat(node.property("surfacing-progress-gap")).hasValue("0");
    }

    @Test
    void surfacingWithoutProgress_computesGap() {
        String goalId = createGoal("Buy gift");
        recordSurfacing(goalId, "agent-1");
        recordSurfacing(goalId, "agent-1");
        recordSurfacing(goalId, "agent-1");

        phase.run(TENANT, List.of());

        MindMapNode node = mindMapStore.getNode(goalId, TENANT);
        assertThat(node.property("surfacing-progress-gap")).hasValue("3");
    }

    @Test
    void progressResetsGap() {
        String goalId = createGoal("Buy gift");
        recordSurfacing(goalId, "agent-1");
        recordSurfacing(goalId, "agent-1");
        recordProgress(goalId, "agent-1");
        recordSurfacing(goalId, "agent-1");

        phase.run(TENANT, List.of());

        MindMapNode node = mindMapStore.getNode(goalId, TENANT);
        assertThat(node.property("surfaced-count")).hasValue("3");
        assertThat(node.property("surfacing-progress-gap")).hasValue("1");
    }

    @Test
    void noGoalSubgraph_doesNothing() {
        var emptyMindMap = new InMemoryMindMapStore();
        var emptyPhase = new SurfacingAggregationPhase(emptyMindMap, memoryStore);
        emptyPhase.run(TENANT, List.of());
    }

    private String createGoal(String name) {
        return mindMapStore.addNode(
                NodeInput.of(name, goalSubgraphId)
                        .withProperties(Map.of("description", name, "status", "active")),
                TENANT);
    }

    private void recordSurfacing(String goalNodeId, String agentId) {
        memoryStore.store(MemoryInput.of(Subject.of("agent", agentId), EXPERIENCE, TENANT,
                "Goal surfaced: " + goalNodeId)
                .withAttributes(Map.of(
                        "cognitive-event", "goal-surfaced",
                        "goal-node-id", goalNodeId)));
    }

    private void recordProgress(String goalNodeId, String agentId) {
        memoryStore.store(MemoryInput.of(Subject.of("agent", agentId), EXPERIENCE, TENANT,
                "Progress on goal: " + goalNodeId)
                .withAttributes(Map.of(
                        "cognitive-event", "goal-progress",
                        "goal-node-id", goalNodeId)));
    }
}
