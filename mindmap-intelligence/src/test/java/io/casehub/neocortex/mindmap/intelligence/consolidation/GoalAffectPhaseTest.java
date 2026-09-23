package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoalAffectPhaseTest {

    private InMemoryMindMapStore store;
    private String goalSubgraphId;
    private static final String TENANT = "t1";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        goalSubgraphId = store.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    @Test
    void phaseNameAndPriority() {
        var phase = new GoalAffectPhase(store);
        assertThat(phase.name()).isEqualTo("goal-affect");
    }

    @Test
    void highUrgency_increasesArousal() {
        String goalId = store.addNode(NodeInput.of("Urgent goal", goalSubgraphId)
                .withProperties(Map.of("description", "urgent",
                        "status", "active", "urgency", "0.9")), TENANT);

        new GoalAffectPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.arousal()).isNotNull();
        assertThat(goal.arousal()).isGreaterThan(0.0);
    }

    @Test
    void blockedGoalWithHighUrgency_causesFrustration() {
        String goalId = store.addNode(NodeInput.of("Blocked goal", goalSubgraphId)
                .withProperties(Map.of("description", "blocked",
                        "status", "blocked", "urgency", "0.9")), TENANT);

        new GoalAffectPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.pleasure()).isNotNull();
        assertThat(goal.pleasure()).isLessThan(0.0);
        assertThat(goal.arousal()).isNotNull();
        assertThat(goal.arousal()).isGreaterThan(0.0);
    }

    @Test
    void completedGoal_positivePleasure() {
        String goalId = store.addNode(NodeInput.of("Done goal", goalSubgraphId)
                .withProperties(Map.of("description", "done",
                        "status", "completed")), TENANT);

        new GoalAffectPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.pleasure()).isNotNull();
        assertThat(goal.pleasure()).isGreaterThan(0.0);
    }

    @Test
    void dormantGoal_reducedArousal() {
        String goalId = store.addNode(NodeInput.of("Dormant goal", goalSubgraphId)
                .withProperties(Map.of("description", "dormant",
                        "status", "dormant")), TENANT);

        new GoalAffectPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.arousal()).isNotNull();
        assertThat(goal.arousal()).isLessThanOrEqualTo(0.0);
    }

    @Test
    void noGoalSubgraph_doesNothing() {
        var emptyStore = new InMemoryMindMapStore();
        new GoalAffectPhase(emptyStore).run(TENANT, List.of());
    }

    @Test
    void dynamicUrgency_drivesArousalForActiveGoal() {
        java.time.Clock clock = java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-23T12:00:00Z"),
                java.time.ZoneOffset.UTC);
        // target-date 1 day away, medium horizon → high dynamic urgency
        String goalId = store.addNode(NodeInput.of("Approaching goal", goalSubgraphId)
                                               .withProperties(Map.of("description", "approaching",
                                                                      "status", "active",
                                                                      "target-date", "2026-09-24T12:00:00Z",
                                                                      "horizon", "medium")), TENANT);

        new GoalAffectPhase(store, clock).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        // dynamic urgency = 1.0 - (1/7) ≈ 0.857, arousal = urgency * 0.8 ≈ 0.686
        assertThat(goal.arousal()).isNotNull();
        assertThat(goal.arousal()).isGreaterThan(0.5);
    }

    @Test
    void dynamicUrgency_drivesArousalForBlockedGoal() {
        java.time.Clock clock = java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-23T12:00:00Z"),
                java.time.ZoneOffset.UTC);
        // target-date 1 day away, blocked → high frustration
        String goalId = store.addNode(NodeInput.of("Blocked approaching", goalSubgraphId)
                                               .withProperties(Map.of("description", "blocked",
                                                                      "status", "blocked",
                                                                      "target-date", "2026-09-24T12:00:00Z",
                                                                      "horizon", "medium")), TENANT);

        new GoalAffectPhase(store, clock).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        // dynamic urgency ≈ 0.857, pleasure = -0.4 - (0.857 * 0.4) ≈ -0.743
        assertThat(goal.pleasure()).isNotNull();
        assertThat(goal.pleasure()).isLessThan(-0.5);
        assertThat(goal.arousal()).isNotNull();
        assertThat(goal.arousal()).isGreaterThan(0.5);
    }

    @Test
    void staticUrgency_usedWhenNoTargetDate() {
        java.time.Clock clock = java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-23T12:00:00Z"),
                java.time.ZoneOffset.UTC);
        String goalId = store.addNode(NodeInput.of("Static goal", goalSubgraphId)
                                               .withProperties(Map.of("description", "static",
                                                                      "status", "active",
                                                                      "urgency", "0.5")), TENANT);

        new GoalAffectPhase(store, clock).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        // arousal = 0.5 * 0.8 = 0.4
        assertThat(goal.arousal()).isNotNull();
        assertThat(goal.arousal()).isCloseTo(0.4, org.assertj.core.api.Assertions.within(0.01));
    }

}
