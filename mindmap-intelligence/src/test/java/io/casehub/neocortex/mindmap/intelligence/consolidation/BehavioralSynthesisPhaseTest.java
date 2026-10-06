package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.*;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class BehavioralSynthesisPhaseTest {

    private InMemoryMindMapStore store;
    private StubCapsEngine capsEngine;
    private BehavioralSynthesisPhase phase;
    private String cognitiveSubgraphId;
    private static final String TENANT = "t1";
    private static final String AGENT = "agent1";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        capsEngine = new StubCapsEngine();
        SituationClassifier classifier = (desc, meta) ->
            List.of(new SituationActivation("reward", 0.8));

        phase = new BehavioralSynthesisPhase(store, capsEngine, classifier, 20);

        cognitiveSubgraphId = store.createSubgraph(
            new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), TENANT);

        capsEngine.initializeAgent(TENANT, AGENT,
            new DispositionAxes("cooperative", "moderate", "calculated",
                "moderate", "analytical"));
    }

    @Test
    void phaseNameIsBehavioralSynthesis() {
        assertThat(phase.name()).isEqualTo("behavioral-synthesis");
    }

    @Test
    void skipsWhenNoCapsEngine() {
        var noEngine = new BehavioralSynthesisPhase(
            store, null, (d, m) -> List.of(), 20);
        noEngine.run(TENANT, List.of());
    }

    @Test
    void skipsWhenNoGraduatedNodes() {
        phase.run(TENANT, List.of());
        List<MindMapNode> behavioral = store.search(
            MindMapQuery.of(TENANT, 100).withType(SubgraphTypes.BEHAVIORAL));
        assertThat(behavioral).isEmpty();
    }

    @Test
    void createsAttractorFromGraduatedExperience() {
        store.addNode(NodeInput.of("Was rewarded for helping", cognitiveSubgraphId)
            .withProperties(Map.of(
                "source-memory-id", "mem1",
                "agent-id", AGENT,
                "event-type", "observation",
                "graduation-score", "0.7")),
            TENANT);

        phase.run(TENANT, List.of());

        List<MindMapNode> behavioral = store.search(
            MindMapQuery.of(TENANT, 100).withType(SubgraphTypes.BEHAVIORAL));
        assertThat(behavioral).isNotEmpty();
        assertThat(behavioral).anySatisfy(n -> {
            assertThat(n.traits()).contains("CapsGenerated");
            assertThat(n.property("agent-id")).hasValue(AGENT);
            assertThat(n.property("category")).isPresent();
        });
    }

    @Test
    void strengthensExistingAttractor() {
        store.addNode(NodeInput.of("Praised for hard work", cognitiveSubgraphId)
            .withProperties(Map.of(
                "source-memory-id", "mem1",
                "agent-id", AGENT,
                "event-type", "observation",
                "graduation-score", "0.6")),
            TENANT);

        phase.run(TENANT, List.of());

        List<MindMapNode> behavioral1 = store.search(
            MindMapQuery.of(TENANT, 100).withType(SubgraphTypes.BEHAVIORAL));
        assertThat(behavioral1).hasSize(1);
        double firstStrength = Double.parseDouble(
            behavioral1.getFirst().property("strength").orElse("0"));

        store.addNode(NodeInput.of("Got a bonus for performance", cognitiveSubgraphId)
            .withProperties(Map.of(
                "source-memory-id", "mem2",
                "agent-id", AGENT,
                "event-type", "observation",
                "graduation-score", "0.8")),
            TENANT);

        phase.run(TENANT, List.of());

        List<MindMapNode> behavioral2 = store.search(
            MindMapQuery.of(TENANT, 100).withType(SubgraphTypes.BEHAVIORAL));
        assertThat(behavioral2).hasSize(1);
        assertThat(behavioral2.getFirst().property("generation")).isPresent();
    }

    @Test
    void updatesWeightsAfterSettling() {
        store.addNode(NodeInput.of("Rewarded consistently", cognitiveSubgraphId)
            .withProperties(Map.of(
                "source-memory-id", "mem1",
                "agent-id", AGENT,
                "event-type", "observation",
                "graduation-score", "0.9")),
            TENANT);

        long genBefore = capsEngine.loadState(TENANT, AGENT).generation();
        phase.run(TENANT, List.of());
        long genAfter = capsEngine.loadState(TENANT, AGENT).generation();

        assertThat(genAfter).isGreaterThan(genBefore);
    }


    @Test
    void emotionBearingNodesUseTargetedReinforcement() {
        var emotionEngine = new EmotionAwareStubCapsEngine();
        emotionEngine.initializeAgent(TENANT, AGENT,
                                      new io.casehub.neocortex.cognitive.index.DispositionAxes("cooperative", "moderate", "calculated", "moderate", "analytical"));
        SituationClassifier classifier = (desc, meta) ->
                                                 List.of(new SituationActivation("reward", 0.8));
        var emotionPhase = new BehavioralSynthesisPhase(store, emotionEngine, classifier, 20);

        store.addNode(NodeInput.of("OCC emotion: FEAR", cognitiveSubgraphId)
                               .withProperties(Map.of(
                                       "source-memory-id", "mem-e1",
                                       "agent-id", AGENT,
                                       "event-type", "observation",
                                       "emotion-type", "FEAR",
                                       "emotion-intensity", "0.7"))
                               .withPad(-0.64, 0.60, -0.43),
                      TENANT);

        emotionPhase.run(TENANT, List.of());

        assertThat(emotionEngine.lastEmotionUpdate).isNotNull();
        assertThat(emotionEngine.lastEmotionUpdate.valence).isEqualTo(-1.0);
        assertThat(emotionEngine.lastEmotionUpdate.intensity).isEqualTo(0.7);
        assertThat(emotionEngine.lastEmotionUpdate.salienceMultiplier).isGreaterThan(1.0);
        assertThat(emotionEngine.lastEmotionUpdate.activations).containsKey("punishment");
    }

    @Test
    void nodesWithoutEmotionUsePadFallback() {
        var emotionEngine = new EmotionAwareStubCapsEngine();
        emotionEngine.initializeAgent(TENANT, AGENT,
                                      new io.casehub.neocortex.cognitive.index.DispositionAxes("cooperative", "moderate", "calculated", "moderate", "analytical"));
        SituationClassifier classifier = (desc, meta) ->
                                                 List.of(new SituationActivation("reward", 0.8));
        var emotionPhase = new BehavioralSynthesisPhase(store, emotionEngine, classifier, 20);

        store.addNode(NodeInput.of("Generic experience", cognitiveSubgraphId)
                               .withProperties(Map.of(
                                       "source-memory-id", "mem-r1",
                                       "agent-id", AGENT,
                                       "event-type", "observation",
                                       "graduation-score", "0.5"))
                               .withPad(0.3, 0.2, 0.1),
                      TENANT);

        emotionPhase.run(TENANT, List.of());

        assertThat(emotionEngine.lastEmotionUpdate).isNull();
        assertThat(emotionEngine.lastRegularUpdate).isNotNull();
        assertThat(emotionEngine.lastRegularUpdate.salienceMultiplier).isEqualTo(1.0);
    }

    static class StubCapsEngine implements CapsEngine {
        private final Map<String, AgentCapsState> states = new ConcurrentHashMap<>();

        @Override
        public CapsTopology topology() { return null; }

        @Override
        public AgentCapsState loadState(String tenantId, String agentId) {
            return states.get(tenantId + "/" + agentId);
        }

        @Override
        public void saveState(AgentCapsState state) {
            states.put(state.tenantId() + "/" + state.agentId(), state);
        }

        @Override
        public AgentCapsState initializeAgent(String tenantId, String agentId,
                                               DispositionAxes disposition) {
            Map<String, ConnectionWeight> weights = new HashMap<>();
            weights.put("reward__BAS_activation",
                new ConnectionWeight(0.5, 0.0, 1.0, 1.0));
            AgentCapsState state = new AgentCapsState(agentId, tenantId, 0,
                weights, Map.of());
            saveState(state);
            return state;
        }

        @Override
        public SettlingResult settle(AgentCapsState state,
                                     Map<String, Double> inputActivations) {
            return new SettlingResult(
                inputActivations, 5, ConvergenceType.CONVERGED, 0.1,
                List.of(new BehavioralAttractor(
                    "approach_avoidance", "approach", 0.6,
                    false, state.generation())));
        }

        @Override
        public AgentCapsState updateWeights(AgentCapsState state,
                                             Map<String, Double> inputActivations,
                                             double outcomeIntensity,
                                             double outcomeValence,
                                             double salienceMultiplier,
                                             String reinforcementSchedule) {
            return new AgentCapsState(state.agentId(), state.tenantId(),
                state.generation() + 1, state.weights(), state.nodeStates());
        }
    }

    record WeightUpdate(Map<String, Double> activations, double intensity, double valence, double salienceMultiplier) {}

    static class EmotionAwareStubCapsEngine extends StubCapsEngine {
        WeightUpdate lastEmotionUpdate;
        WeightUpdate lastRegularUpdate;

        @Override
        public CapsTopology topology() {
            return new CapsTopology(1, Map.of(), List.of(
                    new CapsConnection("c1", "punishment", "FFFS_activation", 0.5,
                                       WeightProvenance.EMPIRICAL, "caps-topology", List.of("threat")),
                    new CapsConnection("c2", "arousal", "BIS_activation", 0.5,
                                       WeightProvenance.EMPIRICAL, "caps-topology", List.of("bis")),
                    new CapsConnection("c3", "reward", "BAS_activation", 0.5,
                                       WeightProvenance.EMPIRICAL, "caps-topology", List.of("bas")),
                    new CapsConnection("c4", "powerlessness", "freeze", 0.3,
                                       WeightProvenance.EMPIRICAL, "caps-topology", List.of("compliance")),
                    new CapsConnection("c5", "powerlessness", "fawn", 0.3,
                                       WeightProvenance.EMPIRICAL, "caps-topology", List.of("fawn_accommodate")),
                    new CapsConnection("c6", "FFFS_activation", "fight", 0.4,
                                       WeightProvenance.EMPIRICAL, "caps-topology", List.of("fight_assert"))
                                                        ), Map.of(), List.of(), null);
        }

        @Override
        public AgentCapsState updateWeights(AgentCapsState state,
                                            Map<String, Double> inputActivations,
                                            double outcomeIntensity,
                                            double outcomeValence,
                                            double salienceMultiplier,
                                            String reinforcementSchedule) {
            var update = new WeightUpdate(inputActivations, outcomeIntensity, outcomeValence, salienceMultiplier);
            if (salienceMultiplier != 1.0) {
                lastEmotionUpdate = update;
            } else {
                lastRegularUpdate = update;
            }
            return super.updateWeights(state, inputActivations, outcomeIntensity, outcomeValence, salienceMultiplier, reinforcementSchedule);
        }
    }
}
