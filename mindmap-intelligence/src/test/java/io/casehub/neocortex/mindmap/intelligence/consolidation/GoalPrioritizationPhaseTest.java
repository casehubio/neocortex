package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.EdgeInput;
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

class GoalPrioritizationPhaseTest {

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
        var phase = new GoalPrioritizationPhase(store);
        assertThat(phase.name()).isEqualTo("goal-prioritization");
    }

    @Test
    void computesPriorityFromFormula() {
        String goalId = store.addNode(NodeInput.of("Prioritized goal", goalSubgraphId)
                .withProperties(Map.of("description", "test",
                        "status", "active",
                        "urgency", "0.8",
                        "feasibility", "0.6"))
                .withPleasure(0.5).withDominance(0.7), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("priority")).isPresent();
        double priority = Double.parseDouble(goal.property("priority").get());
        assertThat(priority).isBetween(0.0, 1.0);
    }

    @Test
    void importanceFromInboundEdges() {
        String goalA = store.addNode(NodeInput.of("Important goal", goalSubgraphId)
                .withProperties(Map.of("description", "important",
                        "status", "active",
                        "urgency", "0.5", "feasibility", "0.5")), TENANT);
        String goalB = store.addNode(NodeInput.of("Helper 1", goalSubgraphId)
                .withProperties(Map.of("description", "helper1",
                        "status", "active",
                        "urgency", "0.5", "feasibility", "0.5")), TENANT);
        String goalC = store.addNode(NodeInput.of("Helper 2", goalSubgraphId)
                .withProperties(Map.of("description", "helper2",
                        "status", "active",
                        "urgency", "0.5", "feasibility", "0.5")), TENANT);

        store.addEdge(EdgeInput.of(goalB, goalA, "contributes-to"), TENANT);
        store.addEdge(EdgeInput.of(goalC, goalA, "enables"), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        double priorityA = Double.parseDouble(store.getNode(goalA, TENANT).property("priority").get());
        double priorityB = Double.parseDouble(store.getNode(goalB, TENANT).property("priority").get());
        assertThat(priorityA).isGreaterThan(priorityB);
    }

    @Test
    void neutralPadYieldsNeutralValence() {
        String goalId = store.addNode(NodeInput.of("Neutral", goalSubgraphId)
                .withProperties(Map.of("description", "neutral",
                        "status", "active",
                        "urgency", "0.5", "feasibility", "0.5")), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        double priority = Double.parseDouble(goal.property("priority").get());
        double expectedValence = 0.5;
        double expected = 0.3 * 0.5 + 0.2 * 0.5 + 0.2 * expectedValence + 0.3 * 0.0;
        assertThat(priority).isCloseTo(expected, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void skipsNonActiveGoals() {
        String goalId = store.addNode(NodeInput.of("Completed", goalSubgraphId)
                .withProperties(Map.of("description", "completed",
                        "status", "completed",
                        "urgency", "0.8", "feasibility", "0.9")), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("priority")).isEmpty();
    }

    @Test
    void noGoalSubgraph_doesNothing() {
        var emptyStore = new InMemoryMindMapStore();
        new GoalPrioritizationPhase(emptyStore).run(TENANT, List.of());
    }

    @Test
    void dynamicUrgency_usesTargetDateInPriorityFormula() {
        java.time.Clock clock = java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-23T12:00:00Z"),
                java.time.ZoneOffset.UTC);
        // target-date 3.5 days away, medium horizon (7 days) → dynamic urgency ≈ 0.5
        String goalId = store.addNode(NodeInput.of("Dynamic goal", goalSubgraphId)
                                               .withProperties(Map.of("description", "test",
                                                                      "status", "active",
                                                                      "target-date", "2026-09-27",
                                                                      "horizon", "medium",
                                                                      "feasibility", "0.6"))
                                               .withPleasure(0.5).withDominance(0.7), TENANT);

        new GoalPrioritizationPhase(store, clock).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("priority")).isPresent();
        double priority = Double.parseDouble(goal.property("priority").get());
        // urgency ≈ 0.5, feasibility = 0.6, valence = (0.5+0.7+2)/4 = 0.8, importance = 0
        // priority = 0.3*0.5 + 0.2*0.6 + 0.2*0.8 + 0.3*0 = 0.15 + 0.12 + 0.16 = 0.43
        assertThat(priority).isCloseTo(0.43, org.assertj.core.api.Assertions.within(0.05));
    }

    @Test
    void decay_standaloneGoalWithLowConfidence_becomesDormant() {
        String goalId = store.addNode(NodeInput.of("Stale goal", goalSubgraphId)
                                               .withConfidence(io.casehub.neocortex.cognitive.Confidence.stated(
                                                       0.3, java.time.Instant.parse("2026-08-01T00:00:00Z")))
                                               .withProperties(Map.of("description", "stale", "status", "active"))
                                               .withPleasure(0.0), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("dormant");
    }

    @Test
    void decay_standaloneGoalWithVeryLowConfidenceAndNegativeAffect_becomesAbandoned() {
        String goalId = store.addNode(NodeInput.of("Abandoned goal", goalSubgraphId)
                                               .withConfidence(io.casehub.neocortex.cognitive.Confidence.stated(
                                                       0.15, java.time.Instant.parse("2026-07-01T00:00:00Z")))
                                               .withProperties(Map.of("description", "abandoned", "status", "active"))
                                               .withPleasure(-0.3), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("abandoned");
        assertThat(goal.property("abandonment-reason")).isPresent();
    }

    @Test
    void decay_linkedGoalWithLowConfidence_getsDecaySignalNotStatusChange() {
        String goalId = store.addNode(NodeInput.of("Linked stale", goalSubgraphId)
                                               .withConfidence(io.casehub.neocortex.cognitive.Confidence.stated(
                                                       0.3, java.time.Instant.parse("2026-08-01T00:00:00Z")))
                                               .withProperties(Map.of("description", "linked",
                                                                      "status", "active",
                                                                      "eidos-goal-name", "explore-topic"))
                                               .withPleasure(0.0), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("active");
        assertThat(goal.property("decay-signal")).contains("dormant");
    }

    @Test
    void decay_linkedGoalWithVeryLowConfidence_getsAbandonSignal() {
        String goalId = store.addNode(NodeInput.of("Linked abandoned", goalSubgraphId)
                                               .withConfidence(io.casehub.neocortex.cognitive.Confidence.stated(
                                                       0.15, java.time.Instant.parse("2026-07-01T00:00:00Z")))
                                               .withProperties(Map.of("description", "linked abandoned",
                                                                      "status", "active",
                                                                      "eidos-goal-name", "stale-goal"))
                                               .withPleasure(-0.3), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("active");
        assertThat(goal.property("decay-signal")).contains("abandon");
    }

    @Test
    void decay_activeGoalWithHealthyConfidence_unchanged() {
        String goalId = store.addNode(NodeInput.of("Healthy goal", goalSubgraphId)
                                               .withConfidence(io.casehub.neocortex.cognitive.Confidence.stated(
                                                       0.8, java.time.Instant.parse("2026-09-20T00:00:00Z")))
                                               .withProperties(Map.of("description", "healthy", "status", "active"))
                                               .withPleasure(0.0), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("active");
        assertThat(goal.property("decay-signal")).isEmpty();
    }

    @Test
    void decay_nonActiveGoal_skipped() {
        String goalId = store.addNode(NodeInput.of("Completed goal", goalSubgraphId)
                                               .withConfidence(io.casehub.neocortex.cognitive.Confidence.stated(
                                                       0.15, java.time.Instant.parse("2026-07-01T00:00:00Z")))
                                               .withProperties(Map.of("description", "done", "status", "completed"))
                                               .withPleasure(-0.5), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("completed");
    }

    @Test
    void decay_activeGoalWithLowConfidenceButPositiveAffect_unchanged() {
        String goalId = store.addNode(NodeInput.of("Still valued", goalSubgraphId)
                                               .withConfidence(io.casehub.neocortex.cognitive.Confidence.stated(
                                                       0.3, java.time.Instant.parse("2026-08-01T00:00:00Z")))
                                               .withProperties(Map.of("description", "valued", "status", "active"))
                                               .withPleasure(0.5), TENANT);

        new GoalPrioritizationPhase(store).run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("active");
        assertThat(goal.property("decay-signal")).isEmpty();
    }

}
