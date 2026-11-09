package io.casehub.neocortex.cognition.eval;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.SearchBudgetConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.mindmap.ContinuumPosition;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.cognition.drive.CuriosityChannel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveExplorationScenarioTest {

    private static final String TENANT = "test-tenant";
    private static final Instant NOW = Instant.now();

    @Test
    void s1_personalityDifferentiation() {
        var store = new InMemoryMindMapStore();
        var sgId = store.createSubgraph(
            new SubgraphInput("research", SubgraphTypes.RESEARCH_AREA, null), TENANT);

        var node1 = store.addNode(
            NodeInput.of("topic-1", sgId).withConfidence(Confidence.inferred(0.45, NOW)), TENANT);
        var node2 = store.addNode(
            NodeInput.of("topic-2", sgId).withConfidence(Confidence.inferred(0.50, NOW)), TENANT);
        var node3 = store.addNode(
            NodeInput.of("topic-3", sgId).withConfidence(Confidence.inferred(0.55, NOW)), TENANT);

        store.addEdge(EdgeInput.of(node1, node2, "relates-to"), TENANT);
        store.addEdge(EdgeInput.of(node2, node3, "relates-to"), TENANT);
        store.addEdge(EdgeInput.of(node1, node3, "relates-to"), TENANT);

        // Anxious: low convergence midpoint → converges easily, high breadth midpoint → narrow search
        var anxiousConfig = new SearchBudgetConfig(6.0, 0.7, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.3, 1.4, null, 0.85, null, null);
        // Steady: high convergence midpoint → resists convergence, low breadth midpoint → broad search
        var steadyConfig = new SearchBudgetConfig(6.0, 0.3, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.7, 1.4, null, 0.85, null, null);

        var harness = CognitionTestHarness.builder()
            .withMindMapStore(store)
            .agentFromDefaults("anxious", CognitiveDefaults.empty("anxious").withSearchBudgetConfig(anxiousConfig))
            .agentFromDefaults("steady", CognitiveDefaults.empty("steady").withSearchBudgetConfig(steadyConfig))
            .build();

        harness.observe("anxious", TENANT, "unexpected experimental result");
        harness.tick("anxious", TENANT);

        harness.observe("steady", TENANT, "unexpected experimental result");
        harness.tick("steady", TENANT);

        var anxiousBudget = harness.searchBudget("anxious", TENANT);
        var steadyBudget = harness.searchBudget("steady", TENANT);

        assertThat(anxiousBudget).as("anxious agent should have a search budget").isPresent();
        assertThat(steadyBudget).as("steady agent should have a search budget").isPresent();

        assertThat(anxiousBudget.get().convergence())
            .as("anxious agent should have higher convergence (narrowing)")
            .isGreaterThan(steadyBudget.get().convergence());

        assertThat(steadyBudget.get().breadth())
            .as("steady agent should have higher breadth (broader exploration)")
            .isGreaterThan(anxiousBudget.get().breadth());

        assertThat(harness.renderPrompt("anxious", TENANT)).isNotNull().isNotEmpty();
        assertThat(harness.renderPrompt("steady", TENANT)).isNotNull().isNotEmpty();

        for (var budget : List.of(anxiousBudget.get(), steadyBudget.get())) {
            assertThat(budget.breadth()).isBetween(0.0, 1.0);
            assertThat(budget.laterality()).isBetween(0.0, 1.0);
            assertThat(budget.depth()).isBetween(0.0, 1.0);
            assertThat(budget.convergence()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void s3_recurringThoughtEscalation() {
        var store = new InMemoryMindMapStore();
        var sgId = store.createSubgraph(
            new SubgraphInput("cognitive", SubgraphTypes.CONCEPT, null), TENANT);
        store.addNode(NodeInput.of("project-deadline", sgId)
            .withConfidence(Confidence.stated(0.3, NOW))
            .withPleasure(-0.7).withArousal(0.8)
            .withProperty("agent-id", "agent"), TENANT);

        var harness = CognitionTestHarness.builder()
            .withMindMapStore(store)
            .agentFromDefaults("agent", CognitiveDefaults.empty("agent"))
            .withConsolidationEnabled(true)
            .build();

        var positions = new ArrayList<ContinuumPosition>();
        boolean reachedFixation = false;
        boolean reachedObsession = false;

        for (int tick = 0; tick < 15; tick++) {
            harness.addExperienceMemories("agent", TENANT, "project-deadline", 3);
            harness.tickAndConsolidate("agent", TENANT);

            var cmap = harness.continuumMap("agent");
            var position = cmap.isEmpty() ? ContinuumPosition.HEALTHY
                : cmap.values().iterator().next();
            positions.add(position);

            if (position == ContinuumPosition.FIXATION) reachedFixation = true;
            if (position == ContinuumPosition.OBSESSION) reachedObsession = true;
        }

        assertThat(reachedFixation)
            .as("should reach FIXATION during escalation")
            .isTrue();
        assertThat(reachedObsession)
            .as("should reach OBSESSION during escalation")
            .isTrue();

        int fixationIdx = positions.indexOf(ContinuumPosition.FIXATION);
        int obsessionIdx = positions.indexOf(ContinuumPosition.OBSESSION);
        assertThat(fixationIdx)
            .as("FIXATION must come before OBSESSION")
            .isLessThan(obsessionIdx);

        var prompt = harness.renderPrompt("agent", TENANT);
        assertThat(prompt).isNotNull().isNotEmpty();
    }

    @Test
    void s5_convergenceCollapse() {
        var anxiousConfig = new SearchBudgetConfig(
            6.0, 0.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, 0.8, null, 0.85, null, null);
        var habConfig = new HabituationConfig(0.1, 0.3, 0.8, Map.of());

        var habState = new HabituationState(Map.of("topic", 5), Map.of("topic", 0.5));

        var harness = CognitionTestHarness.builder()
            .agentFromDefaults("agent", CognitiveDefaults.empty("agent")
                .withSearchBudgetConfig(anxiousConfig)
                .withHabituationConfig(habConfig))
            .withHabituationState("agent", habState)
            .build();

        harness.setContinuumPosition("agent", TENANT,
            Map.of("topic", ContinuumPosition.OBSESSION));

        harness.recordMood("agent", TENANT, 0.0, 0.8, 0.0);

        harness.tick("agent", TENANT);

        var budget = harness.searchBudget("agent", TENANT);
        assertThat(budget).isPresent();
        assertThat(budget.get().convergence())
            .as("convergence should collapse under pathological threshold")
            .isEqualTo(0.0);

        assertThat(budget.get().breadth()).isGreaterThan(0.0);
        assertThat(budget.get().depth()).isGreaterThan(0.0);

        var prompt = harness.renderPrompt("agent", TENANT);
        assertThat(prompt).isNotNull().isNotEmpty();
    }

    @Test
    void s2_goalDirectedExploration() {
        var store = new InMemoryMindMapStore();
        var goalSg = store.createSubgraph(
            new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);
        var researchSg = store.createSubgraph(
            new SubgraphInput("research", SubgraphTypes.RESEARCH_AREA, null), TENANT);

        var goalNode = store.addNode(
            NodeInput.of("research-goal", goalSg).withConfidence(Confidence.stated(0.9, NOW)), TENANT);

        var near1 = store.addNode(
            NodeInput.of("near-topic-1", researchSg).withConfidence(Confidence.inferred(0.45, NOW)), TENANT);
        var near2 = store.addNode(
            NodeInput.of("near-topic-2", researchSg).withConfidence(Confidence.inferred(0.50, NOW)), TENANT);
        store.addEdge(EdgeInput.of(goalNode, near1, "enables"), TENANT);
        store.addEdge(EdgeInput.of(goalNode, near2, "enables"), TENANT);

        var mid1 = store.addNode(
            NodeInput.of("mid-topic-1", researchSg).withConfidence(Confidence.inferred(0.45, NOW)), TENANT);
        var mid2 = store.addNode(
            NodeInput.of("mid-topic-2", researchSg).withConfidence(Confidence.inferred(0.50, NOW)), TENANT);
        store.addEdge(EdgeInput.of(near1, mid1, "relates-to"), TENANT);
        store.addEdge(EdgeInput.of(near2, mid2, "relates-to"), TENANT);

        var far1 = store.addNode(
            NodeInput.of("unrelated-1", researchSg).withConfidence(Confidence.inferred(0.45, NOW)), TENANT);
        var far2 = store.addNode(
            NodeInput.of("unrelated-2", researchSg).withConfidence(Confidence.inferred(0.50, NOW)), TENANT);
        var far3 = store.addNode(
            NodeInput.of("unrelated-3", researchSg).withConfidence(Confidence.inferred(0.55, NOW)), TENANT);
        store.addEdge(EdgeInput.of(far1, far2, "relates-to"), TENANT);
        store.addEdge(EdgeInput.of(far2, far3, "relates-to"), TENANT);

        var harness = CognitionTestHarness.builder()
            .withMindMapStore(store)
            .agentFromDefaults("explorer", CognitiveDefaults.empty("explorer"))
            .withGoalNodeIds(() -> Set.of(goalNode))
            .build();

        harness.observe("explorer", TENANT, "exploring research landscape");
        harness.tick("explorer", TENANT);

        var gaps = harness.lastGaps("explorer");
        assertThat(gaps).isNotEmpty();

        var nearGaps = gaps.stream()
            .filter(g -> g.nodeName().startsWith("near-topic"))
            .toList();
        assertThat(nearGaps).as("should detect gaps near goal").isNotEmpty();
        nearGaps.forEach(g ->
            assertThat(g.goalRelevance()).as("distance-1: " + g.nodeName()).isEqualTo(1.0));

        var midGaps = gaps.stream()
            .filter(g -> g.nodeName().startsWith("mid-topic"))
            .toList();
        if (!midGaps.isEmpty()) {
            midGaps.forEach(g ->
                assertThat(g.goalRelevance()).as("distance-2: " + g.nodeName()).isEqualTo(0.7));
        }

        var farGaps = gaps.stream()
            .filter(g -> g.nodeName().startsWith("unrelated"))
            .toList();
        if (!farGaps.isEmpty()) {
            farGaps.forEach(g ->
                assertThat(g.goalRelevance()).as("unrelated: " + g.nodeName()).isEqualTo(0.0));
        }

        if (nearGaps.size() >= 1 && !gaps.isEmpty()) {
            assertThat(gaps.getFirst().goalRelevance())
                .as("top-ranked gap should be goal-relevant")
                .isGreaterThan(0.0);
        }

        var prompt = harness.renderPrompt("explorer", TENANT);
        assertThat(prompt).isNotNull().isNotEmpty();
    }

    @Test
    void s4_curiosityChannelShifts() {
        var store1 = new InMemoryMindMapStore();
        var sg1a = store1.createSubgraph(
            new SubgraphInput("research", SubgraphTypes.RESEARCH_AREA, null), TENANT);
        var sg1b = store1.createSubgraph(
            new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        var sg1c = store1.createSubgraph(
            new SubgraphInput("projects", SubgraphTypes.PROJECT, null), TENANT);

        var smallNodes = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            smallNodes.add(store1.addNode(
                NodeInput.of("research-" + i, sg1a).withConfidence(Confidence.inferred(0.55, NOW)), TENANT));
        }
        for (int i = 0; i < 3; i++) {
            smallNodes.add(store1.addNode(
                NodeInput.of("concept-" + i, sg1b).withConfidence(Confidence.inferred(0.50, NOW)), TENANT));
        }
        for (int i = 0; i < 2; i++) {
            smallNodes.add(store1.addNode(
                NodeInput.of("project-" + i, sg1c).withConfidence(Confidence.inferred(0.55, NOW)), TENANT));
        }
        for (int i = 0; i < smallNodes.size() - 1; i++) {
            store1.addEdge(EdgeInput.of(smallNodes.get(i), smallNodes.get(i + 1), "relates-to"), TENANT);
        }

        var harness1 = CognitionTestHarness.builder()
            .withMindMapStore(store1)
            .agentFromDefaults("explorer", CognitiveDefaults.empty("explorer"))
            .build();

        harness1.observe("explorer", TENANT, "surveying the landscape");
        harness1.tick("explorer", TENANT);

        var channel1 = harness1.lastChannel("explorer");
        var budget1 = harness1.searchBudget("explorer", TENANT);

        assertThat(channel1).isNotNull();
        assertThat(budget1).isPresent();

        var store2 = new InMemoryMindMapStore();
        var sg2 = store2.createSubgraph(
            new SubgraphInput("research", SubgraphTypes.RESEARCH_AREA, null), TENANT);
        var deepNode = store2.addNode(
            NodeInput.of("deep-topic", sg2).withConfidence(Confidence.inferred(0.2, NOW)), TENANT);
        for (int i = 0; i < 6; i++) {
            var connected = store2.addNode(
                NodeInput.of("connected-" + i, sg2).withConfidence(Confidence.stated(0.98, NOW)), TENANT);
            store2.addEdge(EdgeInput.of(deepNode, connected, "relates-to"), TENANT);
        }

        var harness2 = CognitionTestHarness.builder()
            .withMindMapStore(store2)
            .agentFromDefaults("explorer", CognitiveDefaults.empty("explorer"))
            .build();

        harness2.observe("explorer", TENANT, "diving deep into one topic");
        harness2.tick("explorer", TENANT);

        var channel2 = harness2.lastChannel("explorer");
        var budget2 = harness2.searchBudget("explorer", TENANT);

        assertThat(channel2).isNotNull();
        assertThat(budget2).isPresent();

        assertThat(channel1.focus())
            .as("many small gaps should produce DIVERSIVE focus")
            .isNotEqualTo(CuriosityChannel.FocusType.SPECIFIC);

        assertThat(channel2.focus())
            .as("single deep gap should produce SPECIFIC focus")
            .isEqualTo(CuriosityChannel.FocusType.SPECIFIC);
    }
}
