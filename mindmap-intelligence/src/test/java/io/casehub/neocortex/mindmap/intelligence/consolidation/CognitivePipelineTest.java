package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.index.DispositionAxes;

import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.FormativeAttributeKeys;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the cognitive pipeline stages in isolation and combined:
 * graduation → behavioral synthesis → behavioral rendering.
 * No CDI, no LLM calls — runs in milliseconds.
 */
class CognitivePipelineTest {

    private static final String TENANT = "test";
    private static final String AGENT_A = "agent-a";
    private static final String AGENT_B = "agent-b";

    private InMemoryMemoryStore memoryStore;
    private InMemoryMindMapStore mindMapStore;
    private BehavioralSynthesisPhaseTest.StubCapsEngine capsEngine;
    private SituationClassifier classifier;

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @BeforeEach
    void setUp() {
        memoryStore = new InMemoryMemoryStore(principal);
        mindMapStore = new InMemoryMindMapStore();
        capsEngine = new BehavioralSynthesisPhaseTest.StubCapsEngine();
        classifier = (desc, meta) ->
            List.of(new SituationActivation("reward", 0.8));
    }

    private String storeFormativeMemory(String agentId, String description,
                                         double confidence, double salience,
                                         Double pleasure, Double arousal, Double dominance) {
        var attrs = new HashMap<String, String>();
        attrs.put(ExperienceAttributeKeys.EVENT_TYPE, "formative");
        attrs.put(FormativeAttributeKeys.SALIENCE_MULTIPLIER, String.valueOf(salience));
        attrs.put(FormativeAttributeKeys.CATALOGUE_ENTRY_ID, "test-entry");
        attrs.put(FormativeAttributeKeys.SITUATION_TYPES, "formation");
        return memoryStore.store(new MemoryInput(
            Subject.of("agent", agentId),
            ExperienceEvents.DOMAIN,
            TENANT, null, description, attrs,
            Confidence.unknown(confidence),
            pleasure, arousal, dominance, null, null));
    }

    // --- Stage 1: Graduation ---

    @Test
    void stage1_formativeMemoriesGraduateAsCognitiveNodes() {
        storeFormativeMemory(AGENT_A, "childhood experience A", 0.9, 1.5, -0.5, 0.3, -0.2);
        storeFormativeMemory(AGENT_B, "childhood experience B", 0.8, 1.5, 0.3, 0.2, 0.1);

        var graduationPhase = new ExperienceConsolidationPhase(
            memoryStore, mindMapStore,
            new DefaultGraduationScorer(1), new DefaultGraduationClassifier(),
            0.5, 20, 1);
        graduationPhase.run(TENANT, List.of());

        var cognitive = mindMapStore.search(
            MindMapQuery.of(TENANT, 100).withType(SubgraphTypes.COGNITIVE));
        var graduated = cognitive.stream()
            .filter(n -> n.property("source-memory-id").isPresent())
            .toList();

        assertThat(graduated).hasSize(2);
        assertThat(graduated).anySatisfy(n ->
            assertThat(n.property("agent-id")).hasValue(AGENT_A));
        assertThat(graduated).anySatisfy(n ->
            assertThat(n.property("agent-id")).hasValue(AGENT_B));
    }

    // --- Stage 2: Behavioral Synthesis ---

    @Test
    void stage2_graduatedNodesProduceBehavioralAttractors() {
        storeFormativeMemory(AGENT_A, "formative A", 0.9, 1.5, null, null, null);
        capsEngine.initializeAgent(TENANT, AGENT_A,
            new DispositionAxes("competitive", "flexible", "bold", "high", "competitive"));

        runGraduation();
        runBehavioralSynthesis();

        var behavioral = mindMapStore.search(
            MindMapQuery.of(TENANT, 100).withType(SubgraphTypes.BEHAVIORAL));
        assertThat(behavioral).isNotEmpty();
        assertThat(behavioral).allSatisfy(n -> {
            assertThat(n.traits()).contains("CapsGenerated");
            assertThat(n.property("agent-id")).hasValue(AGENT_A);
        });
    }

    // --- Combined: Multi-agent pipeline ---

    @Test
    void multiAgent_bothAgentsGetBehavioralAttractors() {
        for (int i = 0; i < 5; i++) {
            storeFormativeMemory(AGENT_A, "agent-a memory " + i, 0.9, 1.5, -0.3, 0.4, -0.1);
        }
        for (int i = 0; i < 4; i++) {
            storeFormativeMemory(AGENT_B, "agent-b memory " + i, 0.8, 1.5, 0.3, 0.2, 0.1);
        }

        capsEngine.initializeAgent(TENANT, AGENT_A,
            new DispositionAxes("competitive", "flexible", "bold", "high", "competitive"));
        capsEngine.initializeAgent(TENANT, AGENT_B,
            new DispositionAxes("cooperative", "strict", "calculated", "moderate", "cooperative"));

        runGraduation();
        runBehavioralSynthesis();

        var behavioral = mindMapStore.search(
            MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.BEHAVIORAL));
        var agentANodes = behavioral.stream()
            .filter(n -> AGENT_A.equals(n.property("agent-id").orElse(null))).toList();
        var agentBNodes = behavioral.stream()
            .filter(n -> AGENT_B.equals(n.property("agent-id").orElse(null))).toList();

        assertThat(agentANodes).as("Agent A should have behavioral attractors").isNotEmpty();
        assertThat(agentBNodes).as("Agent B should have behavioral attractors").isNotEmpty();
    }

    @Test
    void multiAgent_withPreExistingCognitiveNodes_bothStillGetAttractors() {
        for (int agent = 0; agent < 14; agent++) {
            String sgId = mindMapStore.createSubgraph(
                new io.casehub.neocortex.mindmap.SubgraphInput(
                    "beliefs-agent-" + agent, SubgraphTypes.COGNITIVE, null), TENANT);
            for (int n = 0; n < 8; n++) {
                mindMapStore.addNode(
                    io.casehub.neocortex.mindmap.NodeInput.of("need-" + n, sgId)
                        .withProperties(Map.of("agent-id", "agent-" + agent)),
                    TENANT);
            }
        }

        for (int i = 0; i < 5; i++) {
            storeFormativeMemory(AGENT_A, "agent-a formative " + i, 0.9, 1.5, -0.3, 0.4, -0.1);
            storeFormativeMemory(AGENT_B, "agent-b formative " + i, 0.8, 1.5, 0.3, 0.2, 0.1);
        }

        capsEngine.initializeAgent(TENANT, AGENT_A,
            new DispositionAxes("competitive", "flexible", "bold", "high", "competitive"));
        capsEngine.initializeAgent(TENANT, AGENT_B,
            new DispositionAxes("cooperative", "strict", "calculated", "moderate", "cooperative"));

        runGraduation();

        var allCognitive = mindMapStore.search(
            MindMapQuery.of(TENANT, 500).withType(SubgraphTypes.COGNITIVE));
        var graduated = allCognitive.stream()
            .filter(n -> n.property("source-memory-id").isPresent())
            .toList();
        assertThat(graduated).hasSize(10);

        runBehavioralSynthesis();

        var behavioral = mindMapStore.search(
            MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.BEHAVIORAL));
        var agentANodes = behavioral.stream()
            .filter(n -> AGENT_A.equals(n.property("agent-id").orElse(null))).toList();
        var agentBNodes = behavioral.stream()
            .filter(n -> AGENT_B.equals(n.property("agent-id").orElse(null))).toList();

        assertThat(agentANodes)
            .as("Agent A should have behavioral attractors despite 112 pre-existing cognitive nodes")
            .isNotEmpty();
        assertThat(agentBNodes)
            .as("Agent B should have behavioral attractors despite 112 pre-existing cognitive nodes")
            .isNotEmpty();
    }

    private void runGraduation() {
        var phase = new ExperienceConsolidationPhase(
            memoryStore, mindMapStore,
            new DefaultGraduationScorer(1), new DefaultGraduationClassifier(),
            0.5, 20, 1);
        phase.run(TENANT, List.of());
    }

    private void runBehavioralSynthesis() {
        var phase = new BehavioralSynthesisPhase(
            mindMapStore, capsEngine, classifier, 20);
        phase.run(TENANT, List.of());
    }
}
