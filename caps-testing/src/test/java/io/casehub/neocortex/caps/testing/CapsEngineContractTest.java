package io.casehub.neocortex.caps.testing;

import io.casehub.neocortex.caps.AgentCapsState;
import io.casehub.neocortex.caps.CapsAttractor;
import io.casehub.neocortex.caps.CapsEngine;
import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.ConnectionWeight;
import io.casehub.neocortex.caps.ConvergenceType;
import io.casehub.neocortex.caps.NodeState;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.SettlingResult;
import io.casehub.neocortex.caps.WeightProvenance;
import io.casehub.neocortex.caps.WeightUpdateContext;
import io.casehub.neocortex.cognitive.index.CopingDefaults;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public abstract class CapsEngineContractTest {

    protected abstract CapsEngine createEngine();

    private CapsEngine engine;

    @BeforeEach
    void setUp() {
        engine = createEngine();
    }

    @Test
    void topologyLoaded() {
        assertThat(engine.topology()).isNotNull();
        assertThat(engine.topology().nodes()).isNotEmpty();
        assertThat(engine.topology().connections()).isNotEmpty();
    }

    @Test
    void initializeAgentCreatesState() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        assertThat(state.agentId()).isEqualTo("agent1");
        assertThat(state.tenantId()).isEqualTo("t1");
        assertThat(state.generation()).isEqualTo(0);
        assertThat(state.weights()).isNotEmpty();
    }

    @Test
    void initializeAgentSeedsCopingAttractorsFromCopingDefaults() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");
        var coping = new CopingDefaults(0.8, 0.3, 0.2, 0.7, 0.6, 0.7, 0.2, 0.3);

        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition, coping);

        // High planfulTendency (0.8) → positive planning resting activation
        assertThat(state.nodeStates().get("planning").restingActivation())
                .isGreaterThan(0.0);
        // High reinterpretationTendency would be > 0, but 0.3 is below midpoint → negative
        assertThat(state.nodeStates().get("positive_reinterpretation").restingActivation())
                .isLessThan(0.0);
        // Low avoidanceTendency (0.2) → below midpoint → negative
        assertThat(state.nodeStates().get("avoidance").restingActivation())
                .isLessThan(0.0);
        // High supportSeekingTendency (0.7) → positive for both support nodes
        assertThat(state.nodeStates().get("seek_instrumental_support").restingActivation())
                .isGreaterThan(0.0);
        assertThat(state.nodeStates().get("seek_emotional_support").restingActivation())
                .isGreaterThan(0.0);
        // Moderate acceptanceTendency (0.6) → slightly positive
        assertThat(state.nodeStates().get("coping_acceptance").restingActivation())
                .isGreaterThan(0.0);
        // High confrontationTendency (0.7) → positive active_confrontation resting activation
        assertThat(state.nodeStates().get("active_confrontation").restingActivation())
                .isGreaterThan(0.0);
        // Low disengagementTendency (0.2) → below midpoint → negative
        assertThat(state.nodeStates().get("mental_disengagement").restingActivation())
                .isLessThan(0.0);
        // Low suppressionTendency (0.3) → below midpoint → negative
        assertThat(state.nodeStates().get("suppression").restingActivation())
                .isLessThan(0.0);
    }

    @Test
    void initializeAgentWithoutCopingDefaultsDoesNotSeedCopingAttractors() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");

        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        // Without CopingDefaults, coping nodes should have default (zero) resting activation
        NodeState planning = state.nodeStates().get("planning");
        if (planning != null) {
            assertThat(planning.restingActivation()).isEqualTo(0.0);
        }
    }

    @Test
    void highBISInputActivatesAvoidantCopingAttractors() {
        var disposition = new DispositionAxes("collaborative", "principled", "conservative", "semi-autonomous", "avoiding");
        var coping = new CopingDefaults(0.3, 0.3, 0.8, 0.3, 0.3, 0.2, 0.7, 0.6);

        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition, coping);

        var            inputs = Map.of("physical_threat", 0.9, "unpredictable_danger", 0.7);
        SettlingResult result = engine.settle(state, inputs);

        double avoidance = result.activations().getOrDefault("avoidance", 0.0);
        double planning  = result.activations().getOrDefault("planning", 0.0);
        assertThat(avoidance).as("high BIS + high avoidance tendency should activate avoidance")
                             .isGreaterThan(planning);
    }

    @Test
    void copingBiasDirectionallyDifferentiatesProfiles() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");
        var planful  = new CopingDefaults(0.8, 0.5, 0.2, 0.5, 0.5, 0.5, 0.5, 0.5);
        var avoidant = new CopingDefaults(0.2, 0.5, 0.8, 0.5, 0.5, 0.5, 0.5, 0.5);

        AgentCapsState planfulState  = engine.initializeAgent("t1", "planful",  disposition, planful);
        AgentCapsState avoidantState = engine.initializeAgent("t1", "avoidant", disposition, avoidant);

        var moderateInputs = Map.of("physical_threat", 0.4);
        SettlingResult planfulResult  = engine.settle(planfulState, moderateInputs);
        SettlingResult avoidantResult = engine.settle(avoidantState, moderateInputs);

        double planfulGap  = planfulResult.activations().getOrDefault("planning", 0.0)
                - planfulResult.activations().getOrDefault("avoidance", 0.0);
        double avoidantGap = avoidantResult.activations().getOrDefault("planning", 0.0)
                - avoidantResult.activations().getOrDefault("avoidance", 0.0);

        assertThat(planfulGap)
                .as("planful agent should have higher planning-vs-avoidance gap than avoidant agent")
                .isGreaterThan(avoidantGap);

        var strongInputs = Map.of("physical_threat", 0.9, "unpredictable_danger", 0.7);
        SettlingResult planfulStrong  = engine.settle(planfulState, strongInputs);
        SettlingResult avoidantStrong = engine.settle(avoidantState, strongInputs);

        double strongPlanfulGap  = planfulStrong.activations().getOrDefault("planning", 0.0)
                - planfulStrong.activations().getOrDefault("avoidance", 0.0);
        double strongAvoidantGap = avoidantStrong.activations().getOrDefault("planning", 0.0)
                - avoidantStrong.activations().getOrDefault("avoidance", 0.0);

        assertThat(strongPlanfulGap)
                .as("directional property holds under strong threat too")
                .isGreaterThan(strongAvoidantGap);

        double moderateDivergence = planfulGap - avoidantGap;
        double strongDivergence   = strongPlanfulGap - strongAvoidantGap;

        assertThat(moderateDivergence)
                .as("coping bias effect is consistent across threat levels (feedback loops equalize)")
                .isCloseTo(strongDivergence, within(1e-4));
    }


    @Test
    void loadStateReturnsInitializedState() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");
        engine.initializeAgent("t1", "agent1", disposition);

        AgentCapsState loaded = engine.loadState("t1", "agent1");
        assertThat(loaded.agentId()).isEqualTo("agent1");
    }

    @Test
    void saveStateIncrementsGeneration() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        assertThat(state.generation()).isEqualTo(0);

        var updated = new AgentCapsState(
            state.agentId(), state.tenantId(),
            state.generation() + 1,
            state.weights(), state.nodeStates());
        engine.saveState(updated);

        AgentCapsState loaded = engine.loadState("t1", "agent1");
        assertThat(loaded.generation()).isEqualTo(1);
    }

    @Test
    void settlingConvergesWithSimpleInput() {
        var disposition = new DispositionAxes("collaborative", "principled", "bold", "semi-autonomous", "collaborating");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("reward", 0.8);
        SettlingResult result = engine.settle(state, inputs);

        assertThat(result.convergence()).isIn(
            ConvergenceType.CONVERGED, ConvergenceType.OSCILLATION,
            ConvergenceType.MAX_ITERATIONS);
        assertThat(result.iterations()).isGreaterThan(0);
        assertThat(result.activations()).isNotEmpty();
    }

    @Test
    void highBISInputProducesCautiousApproach() {
        var disposition = new DispositionAxes("collaborative", "principled", "conservative", "semi-autonomous", "avoiding");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("physical_threat", 0.9, "unpredictable_danger", 0.7);
        SettlingResult result = engine.settle(state, inputs);

        double cautious = result.activations().getOrDefault("cautious_approach", 0.0);
        double approach = result.activations().getOrDefault("approach", 0.0);
        assertThat(cautious).isGreaterThan(approach);
    }

    @Test
    void weightUpdateModifiesState() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("reward", 0.8);
        double originalWeight = state.weights()
            .getOrDefault("reward__BAS_activation", new ConnectionWeight(0.5, 1.0))
            .excitatory();

        AgentCapsState updated = engine.updateWeights(
            state, new WeightUpdateContext(inputs, 0.3, 1.0, 1.0, "continuous"));

        double newWeight = updated.weights()
            .get("reward__BAS_activation").excitatory();
        assertThat(newWeight).isNotEqualTo(originalWeight);
    }

    @Test
    void empiricalWeightsResistChange() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("reward", 0.8);

        AgentCapsState updated = engine.updateWeights(
            state, new WeightUpdateContext(inputs, 0.3, 1.0, 1.0, "continuous"));

        var empiricalConn = engine.topology().connections().stream()
            .filter(c -> c.provenance() == WeightProvenance.EMPIRICAL)
            .findFirst().orElseThrow();

        var estimatedConn = engine.topology().connections().stream()
            .filter(c -> c.provenance() == WeightProvenance.ESTIMATED)
            .findFirst();

        if (estimatedConn.isPresent()) {
            double empiricalPrecision = updated.weights()
                .getOrDefault(empiricalConn.id(), new ConnectionWeight(0, 10))
                .precision();
            double estimatedPrecision = updated.weights()
                .getOrDefault(estimatedConn.get().id(), new ConnectionWeight(0, 1))
                .precision();
            assertThat(empiricalPrecision).isGreaterThan(estimatedPrecision);
        }
    }

    @Test
    void inputNodesAreClampedDuringSettling() {
        var disposition = new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("reward", 0.8, "punishment", 0.3);
        SettlingResult result = engine.settle(state, inputs);

        assertThat(result.activations().get("reward")).isCloseTo(0.8, within(0.001));
        assertThat(result.activations().get("punishment")).isCloseTo(0.3, within(0.001));
    }

    @Test
    void zeroInputProducesZeroOutputActivation() {
        var state = engine.initializeAgent("t1", "zero-test",
                                           new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "collaborating"));
        var result = engine.settle(state, Map.of());
        for (var entry : result.activations().entrySet()) {
            CapsNode node = engine.topology().nodes().get(entry.getKey());
            if (node != null && node.type() != NodeType.INPUT) {
                assertThat(Math.abs(entry.getValue()))
                        .as("non-input node %s should be near zero with no input", entry.getKey())
                        .isLessThan(0.1);
            }
        }
    }

    @Test
    void autonomyModifiersProduceWeightDifferences() {
        var independent = engine.initializeAgent("t1", "ind",
                                                 new DispositionAxes("collaborative", "principled", "measured", "autonomous", "collaborating"));
        var dependent = engine.initializeAgent("t1", "dep",
                                               new DispositionAxes("collaborative", "principled", "measured", "directed", "collaborating"));
        boolean anyDifference = false;
        for (String connId : independent.weights().keySet()) {
            double wInd = independent.weights().get(connId).excitatory();
            double wDep = dependent.weights().getOrDefault(connId,
                                                           new ConnectionWeight(0, 1)).excitatory();
            if (Math.abs(wInd - wDep) > 0.001) {
                anyDifference = true;
                break;
            }
        }
        assertThat(anyDifference)
                .as("independent vs dependent agents must have different connection weights")
                .isTrue();
    }

    @Test
    void restingActivationsClampedWithinBounds() {
        var state = engine.initializeAgent("t1", "extreme",
                                           new DispositionAxes("collaborative", "defiant", "bold", "autonomous", "confrontational"));
        for (var entry : state.nodeStates().entrySet()) {
            double resting = entry.getValue().restingActivation();
            assertThat(resting)
                    .as("resting activation for %s must be in [-0.5, 0.5]", entry.getKey())
                    .isBetween(-0.5, 0.5);
        }
    }

    @Test
    void noInputRankingDiffersFromThreatRanking() {
        var state = engine.initializeAgent("t1", "ranking",
                                           new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "collaborating"));

        var noInput = engine.settle(state, Map.of());
        var threat  = engine.settle(state, Map.of("physical_threat", 1.0));

        List<String> noInputTop3 = noInput.attractors().stream()
                                          .sorted((a, b) -> Double.compare(b.strength(), a.strength()))
                                          .limit(3)
                                          .map(CapsAttractor::nodeId)
                                          .toList();

        List<String> threatTop3 = threat.attractors().stream()
                                        .sorted((a, b) -> Double.compare(b.strength(), a.strength()))
                                        .limit(3)
                                        .map(CapsAttractor::nodeId)
                                        .toList();

        assertThat(threatTop3)
                .as("top-3 attractors under physical_threat=1.0 must differ from no-input ranking")
                .isNotEqualTo(noInputTop3);
    }

    @Test
    void oppositePersonalitiesDiverge() {
        var bold = engine.initializeAgent("t1", "bold",
                                          new DispositionAxes("collaborative", "defiant", "bold", "autonomous", "confrontational"));
        var cautious = engine.initializeAgent("t1", "cautious",
                                              new DispositionAxes("collaborative", "compliant", "conservative", "directed", "avoiding"));

        var input          = Map.of("physical_threat", 0.7, "social_threat", 0.5);
        var boldResult     = engine.settle(bold, input);
        var cautiousResult = engine.settle(cautious, input);

        double      maxDifference = 0.0;
        Set<String> allNodes      = new HashSet<>(boldResult.activations().keySet());
        allNodes.addAll(cautiousResult.activations().keySet());
        for (String nodeId : allNodes) {
            CapsNode node = engine.topology().nodes().get(nodeId);
            if (node != null && node.type() == NodeType.OUTPUT) {
                double diff = Math.abs(
                        boldResult.activations().getOrDefault(nodeId, 0.0)
                        - cautiousResult.activations().getOrDefault(nodeId, 0.0));
                maxDifference = Math.max(maxDifference, diff);
            }
        }

        assertThat(maxDifference)
                .as("max output activation difference between opposite personalities must be > 0.1")
                .isGreaterThan(0.1);
    }


}
