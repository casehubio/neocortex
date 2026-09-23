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
}
