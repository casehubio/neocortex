package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.CognitiveGoalDecomposer;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.GoalDecompositionResult;
import io.casehub.neocortex.mindmap.GoalLifecycleProvider;
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

class GoalResolutionPhaseTest {

    private InMemoryMindMapStore store;
    private String goalSubgraphId;
    private static final String TENANT = "t1";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        goalSubgraphId = store.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    private GoalResolutionPhase phase(CognitiveGoalDecomposer decomposer,
                                       GoalLifecycleProvider lifecycleProvider) {
        return new GoalResolutionPhase(store, decomposer, lifecycleProvider);
    }

    private GoalResolutionPhase noOpPhase() {
        return phase(
                (desc, ctx, tid) -> GoalDecompositionResult.EMPTY,
                (agentId, tid) -> Map.of());
    }

    // --- Phase metadata ---

    @Test
    void phaseNameAndPriority() {
        var p = noOpPhase();
        assertThat(p.name()).isEqualTo("goal-resolution");
    }

    // --- Prune step ---

    @Test
    void prune_collapsesDistantGoalSubNodes() {
        String parentId = store.addNode(NodeInput.of("Learn ML", goalSubgraphId)
                .withProperties(Map.of("description", "learn machine learning",
                        "status", "active", "horizon", "aspirational",
                        "resolution", "high")), TENANT);

        String sub1 = store.addNode(NodeInput.of("Linear Algebra", goalSubgraphId)
                .withProperties(Map.of("description", "learn linear algebra",
                        "status", "active")), TENANT);
        String sub2 = store.addNode(NodeInput.of("Calculus", goalSubgraphId)
                .withProperties(Map.of("description", "learn calculus",
                        "status", "active")), TENANT);

        store.addEdge(EdgeInput.of(parentId, sub1, "decomposes-into"), TENANT);
        store.addEdge(EdgeInput.of(parentId, sub2, "decomposes-into"), TENANT);

        noOpPhase().run(TENANT, List.of());

        MindMapNode parent = store.getNode(parentId, TENANT);
        assertThat(parent.property("resolution")).contains("low");
        assertThat(store.getNode(sub1, TENANT)).isNull();
        assertThat(store.getNode(sub2, TENANT)).isNull();
    }

    @Test
    void prune_doesNotCollapsUrgentGoals() {
        String parentId = store.addNode(NodeInput.of("Ship feature", goalSubgraphId)
                .withProperties(Map.of("description", "ship the feature",
                        "status", "active", "horizon", "immediate",
                        "resolution", "high", "urgency", "0.9")), TENANT);

        String sub1 = store.addNode(NodeInput.of("Write tests", goalSubgraphId)
                .withProperties(Map.of("description", "write tests",
                        "status", "active")), TENANT);
        store.addEdge(EdgeInput.of(parentId, sub1, "decomposes-into"), TENANT);

        noOpPhase().run(TENANT, List.of());

        assertThat(store.getNode(sub1, TENANT)).isNotNull();
        MindMapNode parent = store.getNode(parentId, TENANT);
        assertThat(parent.property("resolution")).contains("high");
    }

    // --- Revise step ---

    @Test
    void revise_unblocksGoalWhenBlockerCompleted() {
        String goalA = store.addNode(NodeInput.of("Goal A", goalSubgraphId)
                .withProperties(Map.of("description", "goal A",
                        "status", "blocked")), TENANT);
        String goalB = store.addNode(NodeInput.of("Goal B", goalSubgraphId)
                .withProperties(Map.of("description", "goal B",
                        "status", "completed")), TENANT);

        store.addEdge(EdgeInput.of(goalB, goalA, "blocks"), TENANT);

        noOpPhase().run(TENANT, List.of());

        MindMapNode a = store.getNode(goalA, TENANT);
        assertThat(a.property("status")).contains("active");
    }

    @Test
    void revise_staysBlockedWhenBlockerStillActive() {
        String goalA = store.addNode(NodeInput.of("Goal A", goalSubgraphId)
                .withProperties(Map.of("description", "goal A",
                        "status", "blocked")), TENANT);
        String goalB = store.addNode(NodeInput.of("Goal B", goalSubgraphId)
                .withProperties(Map.of("description", "goal B",
                        "status", "active")), TENANT);

        store.addEdge(EdgeInput.of(goalB, goalA, "blocks"), TENANT);

        noOpPhase().run(TENANT, List.of());

        MindMapNode a = store.getNode(goalA, TENANT);
        assertThat(a.property("status")).contains("blocked");
    }

    @Test
    void revise_detectsAndBreaksCycle() {
        String goalA = store.addNode(NodeInput.of("A", goalSubgraphId)
                                              .withProperties(Map.of("description", "a", "status", "active")), TENANT);
        String goalB = store.addNode(NodeInput.of("B", goalSubgraphId)
                                              .withProperties(Map.of("description", "b", "status", "active")), TENANT);
        String goalC = store.addNode(NodeInput.of("C", goalSubgraphId)
                                              .withProperties(Map.of("description", "c", "status", "active")), TENANT);

        String edgeAB = store.addEdge(EdgeInput.of(goalA, goalB, "blocks"), TENANT);
        String edgeBC = store.addEdge(EdgeInput.of(goalB, goalC, "blocks"), TENANT);
        String edgeCA = store.addEdge(EdgeInput.of(goalC, goalA, "blocks"), TENANT);

        noOpPhase().run(TENANT, List.of());

        int removedCount = 0;
        if (store.getEdge(edgeAB, TENANT) == null) {removedCount++;}
        if (store.getEdge(edgeBC, TENANT) == null) {removedCount++;}
        if (store.getEdge(edgeCA, TENANT) == null) {removedCount++;}
        assertThat(removedCount).as("exactly one edge should be removed to break cycle")
                                .isEqualTo(1);
    }

    // --- Sync step ---

    @Test
    void sync_updatesLinkedGoalStatusFromProvider() {
        String goalId = store.addNode(NodeInput.of("Explore topic", goalSubgraphId)
                .withProperties(Map.of("description", "explore topic",
                        "status", "active",
                        "eidos-goal-name", "explore-topic")), TENANT);

        GoalLifecycleProvider provider = (agentId, tid) ->
                Map.of("explore-topic", "completed");

        phase((d, c, t) -> GoalDecompositionResult.EMPTY, provider)
                .run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("completed");
    }

    @Test
    void sync_clearsDecaySignalAfterSync() {
        String goalId = store.addNode(NodeInput.of("Explore topic", goalSubgraphId)
                .withProperties(Map.of("description", "explore topic",
                        "status", "active",
                        "eidos-goal-name", "explore-topic",
                        "decay-signal", "dormant")), TENANT);

        GoalLifecycleProvider provider = (agentId, tid) ->
                Map.of("explore-topic", "active");

        phase((d, c, t) -> GoalDecompositionResult.EMPTY, provider)
                .run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("active");
        assertThat(goal.property("decay-signal")).isEmpty();
    }

    @Test
    void sync_noOpProviderDoesNothing() {
        String goalId = store.addNode(NodeInput.of("Standalone", goalSubgraphId)
                .withProperties(Map.of("description", "standalone goal",
                        "status", "active",
                        "eidos-goal-name", "standalone")), TENANT);

        noOpPhase().run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("active");
    }

    @Test
    void sync_ignoresUnlinkedGoals() {
        String goalId = store.addNode(NodeInput.of("No link", goalSubgraphId)
                .withProperties(Map.of("description", "no link",
                        "status", "active")), TENANT);

        GoalLifecycleProvider provider = (agentId, tid) ->
                Map.of("some-other-goal", "completed");

        phase((d, c, t) -> GoalDecompositionResult.EMPTY, provider)
                .run(TENANT, List.of());

        MindMapNode goal = store.getNode(goalId, TENANT);
        assertThat(goal.property("status")).contains("active");
    }

    // --- Ordering ---


// --- Expand step ---

    @Test
    void expand_decomposesApproachingGoal() {
        String parentId = store.addNode(NodeInput.of("Ship MVP", goalSubgraphId)
                                                 .withProperties(Map.of("description", "ship the MVP",
                                                                        "status", "active", "urgency", "0.9",
                                                                        "resolution", "low")), TENANT);

        CognitiveGoalDecomposer decomposer = (desc, ctx, tid) ->
                                                     new GoalDecompositionResult(
                                                             List.of(new GoalDecompositionResult.SubGoal("write tests", "short", Map.of()),
                                                                     new GoalDecompositionResult.SubGoal("deploy to prod", "immediate", Map.of())),
                                                             List.of(new GoalDecompositionResult.GoalRelationship("ship the MVP", "write tests", "decomposes-into"),
                                                                     new GoalDecompositionResult.GoalRelationship("ship the MVP", "deploy to prod", "decomposes-into")));

        phase(decomposer, (a, t) -> Map.of()).run(TENANT, List.of());

        MindMapNode parent = store.getNode(parentId, TENANT);
        assertThat(parent.property("resolution")).contains("high");

        List<MindMapNode> allGoals = store.nodesIn(goalSubgraphId, TENANT);
        assertThat(allGoals).hasSize(3);
        assertThat(allGoals.stream().map(MindMapNode::name))
                .containsExactlyInAnyOrder("Ship MVP", "write tests", "deploy to prod");
    }

    @Test
    void expand_skipsAlreadyHighResolution() {
        store.addNode(NodeInput.of("Already detailed", goalSubgraphId)
                               .withProperties(Map.of("description", "already detailed",
                                                      "status", "active", "urgency", "0.9",
                                                      "resolution", "high")), TENANT);

        CognitiveGoalDecomposer decomposer = (desc, ctx, tid) -> {
            throw new AssertionError("Should not be called for high-resolution goals");
        };

        phase(decomposer, (a, t) -> Map.of()).run(TENANT, List.of());
    }

    @Test
    void expand_noOpDecomposerDoesNothing() {
        store.addNode(NodeInput.of("Urgent goal", goalSubgraphId)
                               .withProperties(Map.of("description", "urgent",
                                                      "status", "active", "urgency", "0.9",
                                                      "resolution", "low")), TENANT);

        noOpPhase().run(TENANT, List.of());

        List<MindMapNode> allGoals = store.nodesIn(goalSubgraphId, TENANT);
        assertThat(allGoals).hasSize(1);
    }

// --- Merge step ---

    @Test
    void merge_combinesSharedSubGoals() {
        String parent1 = store.addNode(NodeInput.of("Learn AI", goalSubgraphId)
                                                .withProperties(Map.of("description", "learn AI", "status", "active")), TENANT);
        String parent2 = store.addNode(NodeInput.of("Research ML", goalSubgraphId)
                                                .withProperties(Map.of("description", "research ML", "status", "active")), TENANT);

        String sub1 = store.addNode(NodeInput.of("study linear algebra", goalSubgraphId)
                                             .withProperties(Map.of("description", "study linear algebra", "status", "active")), TENANT);
        String sub2 = store.addNode(NodeInput.of("study linear algebra basics", goalSubgraphId)
                                             .withProperties(Map.of("description", "study linear algebra basics", "status", "active")), TENANT);

        store.addEdge(EdgeInput.of(parent1, sub1, "decomposes-into"), TENANT);
        store.addEdge(EdgeInput.of(parent2, sub2, "decomposes-into"), TENANT);

        noOpPhase().run(TENANT, List.of());

        List<MindMapNode> allGoals = store.nodesIn(goalSubgraphId, TENANT);
        assertThat(allGoals).hasSize(3);

        MindMapNode survivingChild = allGoals.stream()
                                             .filter(n -> n.name().contains("linear algebra"))
                                             .findFirst().orElseThrow();
        List<MindMapEdge> contributesTo = store.neighbors(survivingChild.id(), "contributes-to", TENANT);
        assertThat(contributesTo).isNotEmpty();
    }

    @Test
    void runningWithNoGoalSubgraph_doesNothing() {
        var emptyStore = new InMemoryMindMapStore();
        new GoalResolutionPhase(emptyStore,
                (d, c, t) -> GoalDecompositionResult.EMPTY,
                (a, t) -> Map.of()).run(TENANT, List.of());
    }
}
