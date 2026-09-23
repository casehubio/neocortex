package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.CognitiveGoalRecognizer;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.RecognizedGoal;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoalRecognitionPhaseTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private String goalSubgraphId;
    private static final String TENANT = "t1";

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore(principal);
        goalSubgraphId = mindMapStore.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    private GoalRecognitionPhase phase(CognitiveGoalRecognizer recognizer) {
        return new GoalRecognitionPhase(mindMapStore, memoryStore, recognizer);
    }

    @Test
    void phaseNameAndPriority() {
        var p = phase((text, existing, tid) -> List.of());
        assertThat(p.name()).isEqualTo("goal-recognition");
    }

    @Test
    void recognizesGoalFromExperienceMemory() {
        memoryStore.store(MemoryInput.of("agent-1", new MemoryDomain("experience"), TENANT,
                "I really want to learn quantum computing"));

        CognitiveGoalRecognizer recognizer = (text, existing, tid) ->
                List.of(new RecognizedGoal("learn quantum computing",
                        "conversation", "long", 0.85));

        phase(recognizer).run(TENANT, List.of());

        List<MindMapNode> goals = mindMapStore.nodesIn(goalSubgraphId, TENANT);
        assertThat(goals).hasSize(1);
        assertThat(goals.get(0).name()).isEqualTo("learn quantum computing");
        assertThat(goals.get(0).property("origin")).contains("conversation");
        assertThat(goals.get(0).property("horizon")).contains("long");
        assertThat(goals.get(0).property("status")).contains("active");
    }

    @Test
    void deduplicatesAgainstExistingGoals() {
        mindMapStore.addNode(NodeInput.of("learn quantum computing", goalSubgraphId)
                .withProperties(Map.of("description", "learn quantum computing",
                        "status", "active")), TENANT);

        memoryStore.store(MemoryInput.of("agent-1", new MemoryDomain("experience"), TENANT,
                "I really want to learn quantum computing"));

        CognitiveGoalRecognizer recognizer = (text, existing, tid) ->
                List.of(new RecognizedGoal("learn quantum computing",
                        "conversation", "long", 0.85));

        phase(recognizer).run(TENANT, List.of());

        List<MindMapNode> goals = mindMapStore.nodesIn(goalSubgraphId, TENANT);
        assertThat(goals).hasSize(1);
    }

    @Test
    void noOpRecognizerCreatesNothing() {
        memoryStore.store(MemoryInput.of("agent-1", new MemoryDomain("experience"), TENANT,
                "Some experience text"));

        phase((text, existing, tid) -> List.of()).run(TENANT, List.of());

        List<MindMapNode> goals = mindMapStore.nodesIn(goalSubgraphId, TENANT);
        assertThat(goals).isEmpty();
    }

    @Test
    void noMemories_doesNotCallRecognizer() {
        CognitiveGoalRecognizer recognizer = (text, existing, tid) -> {
            throw new AssertionError("Should not be called with no memories");
        };

        phase(recognizer).run(TENANT, List.of());
    }

    @Test
    void noGoalSubgraph_doesNothing() {
        var emptyMindMap = new InMemoryMindMapStore();
        new GoalRecognitionPhase(emptyMindMap, memoryStore,
                (text, existing, tid) -> List.of()).run(TENANT, List.of());
    }
}
