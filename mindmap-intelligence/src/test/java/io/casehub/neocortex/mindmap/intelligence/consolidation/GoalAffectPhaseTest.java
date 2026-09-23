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
}
