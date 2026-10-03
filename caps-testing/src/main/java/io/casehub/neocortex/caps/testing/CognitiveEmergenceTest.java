package io.casehub.neocortex.caps.testing;

import io.casehub.neocortex.caps.*;
import io.casehub.neocortex.caps.engine.CapsSettler;
import io.casehub.neocortex.caps.engine.CapsTopologyLoader;
import io.casehub.neocortex.caps.engine.CapsWeightUpdater;
import io.casehub.neocortex.caps.engine.DispositionWeightMapper;
import io.casehub.neocortex.caps.engine.RuleBasedSituationClassifier;
import io.casehub.neocortex.cognitive.index.DispositionAxes;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class CognitiveEmergenceTest {

    private final CapsTopology topology;
    private final CapsSettler settler;
    private final CapsWeightUpdater weightUpdater;
    private final DispositionWeightMapper dispositionMapper;
    private final RuleBasedSituationClassifier classifier;

    protected CognitiveEmergenceTest() {
        this.topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        this.settler = new CapsSettler(topology);
        this.weightUpdater = new CapsWeightUpdater(topology);
        this.dispositionMapper = new DispositionWeightMapper(topology);
        this.classifier = new RuleBasedSituationClassifier(topology);
    }

    protected CapsTopology topology() { return topology; }
    protected RuleBasedSituationClassifier classifier() { return classifier; }

    protected Scenario scenario(String agentId, DispositionAxes disposition) {
        return new Scenario(agentId, disposition);
    }

    protected class Scenario {
        private final String agentId;
        private final String tenantId = "test";
        private AgentCapsState state;
        private final List<ExperienceInput> experiences = new ArrayList<>();

        Scenario(String agentId, DispositionAxes disposition) {
            this.agentId = agentId;
            this.state = dispositionMapper.initializeWeights(tenantId, agentId, disposition);
        }

        public Scenario withExperience(String description,
                                        double intensity, double valence) {
            experiences.add(new ExperienceInput(description, intensity, valence));
            return this;
        }

        public Scenario withExperience(String description,
                                        double intensity, double valence,
                                        int repetitions) {
            for (int i = 0; i < repetitions; i++) {
                experiences.add(new ExperienceInput(description, intensity, valence));
            }
            return this;
        }

        public Scenario withDirectActivation(Map<String, Double> activations,
                                              double intensity, double valence) {
            state = weightUpdater.update(state, activations,
                intensity, valence, 1.0, "continuous");
            return this;
        }

        public Scenario applyExperiences() {
            for (ExperienceInput exp : experiences) {
                List<SituationActivation> activations = classifier.classify(
                    exp.description(), Map.of());
                if (activations.isEmpty()) continue;

                Map<String, Double> inputMap = new HashMap<>();
                for (SituationActivation sa : activations) {
                    inputMap.put(sa.nodeId(), sa.confidence());
                }

                state = weightUpdater.update(state, inputMap,
                    exp.intensity(), exp.valence(), 1.0, "continuous");
            }
            experiences.clear();
            return this;
        }

        public SettlingResult settle(Map<String, Double> situationActivations) {
            return settler.settle(state, situationActivations);
        }

        public SettlingResult settleFromDescription(String description) {
            List<SituationActivation> activations = classifier.classify(
                description, Map.of());
            Map<String, Double> inputMap = new HashMap<>();
            for (SituationActivation sa : activations) {
                inputMap.put(sa.nodeId(), sa.confidence());
            }
            return settler.settle(state, inputMap);
        }

        public AgentCapsState state() { return state; }
    }

    protected record ExperienceInput(String description, double intensity, double valence) {}

    protected static void assertAttractorPresent(SettlingResult result, String nodeId) {
        assertThat(result.attractors())
            .as("Expected attractor '%s' in %s", nodeId,
                result.attractors().stream().map(BehavioralAttractor::nodeId).toList())
            .anySatisfy(a -> assertThat(a.nodeId()).isEqualTo(nodeId));
    }

    protected static void assertAttractorAbsent(SettlingResult result, String nodeId) {
        assertThat(result.attractors())
            .as("Expected no attractor '%s' but found in %s", nodeId,
                result.attractors().stream().map(BehavioralAttractor::nodeId).toList())
            .noneSatisfy(a -> assertThat(a.nodeId()).isEqualTo(nodeId));
    }

    protected static void assertAttractorStrongerThan(SettlingResult result,
                                                       String strongerId,
                                                       String weakerId) {
        double stronger = result.attractors().stream()
            .filter(a -> a.nodeId().equals(strongerId))
            .mapToDouble(BehavioralAttractor::strength)
            .findFirst().orElse(0.0);
        double weaker = result.attractors().stream()
            .filter(a -> a.nodeId().equals(weakerId))
            .mapToDouble(BehavioralAttractor::strength)
            .findFirst().orElse(0.0);
        assertThat(stronger)
            .as("Expected '%s' (%.3f) > '%s' (%.3f)",
                strongerId, stronger, weakerId, weaker)
            .isGreaterThan(weaker);
    }

    protected static double attractorStrength(SettlingResult result, String nodeId) {
        return result.attractors().stream()
            .filter(a -> a.nodeId().equals(nodeId))
            .mapToDouble(BehavioralAttractor::strength)
            .findFirst().orElse(0.0);
    }
}
