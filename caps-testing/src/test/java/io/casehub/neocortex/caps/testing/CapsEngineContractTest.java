package io.casehub.neocortex.caps.testing;

import io.casehub.neocortex.caps.*;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

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
        var disposition = new DispositionAxes(
            "cooperative", "moderate", "calculated", "moderate", "analytical");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        assertThat(state.agentId()).isEqualTo("agent1");
        assertThat(state.tenantId()).isEqualTo("t1");
        assertThat(state.generation()).isEqualTo(0);
        assertThat(state.weights()).isNotEmpty();
    }

    @Test
    void loadStateReturnsInitializedState() {
        var disposition = new DispositionAxes(
            "cooperative", "moderate", "calculated", "moderate", "analytical");
        engine.initializeAgent("t1", "agent1", disposition);

        AgentCapsState loaded = engine.loadState("t1", "agent1");
        assertThat(loaded.agentId()).isEqualTo("agent1");
    }

    @Test
    void saveStateIncrementsGeneration() {
        var disposition = new DispositionAxes(
            "cooperative", "moderate", "calculated", "moderate", "analytical");
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
        var disposition = new DispositionAxes(
            "cooperative", "moderate", "bold", "moderate", "cooperative");
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
        var disposition = new DispositionAxes(
            "cooperative", "moderate", "conservative", "moderate", "avoidant");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("physical_threat", 0.9, "unpredictable_danger", 0.7);
        SettlingResult result = engine.settle(state, inputs);

        double cautious = result.activations().getOrDefault("cautious_approach", 0.0);
        double approach = result.activations().getOrDefault("approach", 0.0);
        assertThat(cautious).isGreaterThan(approach);
    }

    @Test
    void weightUpdateModifiesState() {
        var disposition = new DispositionAxes(
            "cooperative", "moderate", "calculated", "moderate", "analytical");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("reward", 0.8);
        double originalWeight = state.weights()
            .getOrDefault("reward__BAS_activation", new ConnectionWeight(0.5, 0.0, 1.0, 1.0))
            .excitatory();

        AgentCapsState updated = engine.updateWeights(
            state, inputs, 0.3, 1.0, 1.0, "continuous");

        double newWeight = updated.weights()
            .get("reward__BAS_activation").excitatory();
        assertThat(newWeight).isNotEqualTo(originalWeight);
    }

    @Test
    void empiricalWeightsResistChange() {
        var disposition = new DispositionAxes(
            "cooperative", "moderate", "calculated", "moderate", "analytical");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("reward", 0.8);

        AgentCapsState updated = engine.updateWeights(
            state, inputs, 0.3, 1.0, 1.0, "continuous");

        var empiricalConn = engine.topology().connections().stream()
            .filter(c -> c.provenance() == WeightProvenance.EMPIRICAL)
            .findFirst().orElseThrow();

        var estimatedConn = engine.topology().connections().stream()
            .filter(c -> c.provenance() == WeightProvenance.ESTIMATED)
            .findFirst();

        if (estimatedConn.isPresent()) {
            double empiricalPrecision = updated.weights()
                .getOrDefault(empiricalConn.id(), new ConnectionWeight(0, 0, 1, 10))
                .precision();
            double estimatedPrecision = updated.weights()
                .getOrDefault(estimatedConn.get().id(), new ConnectionWeight(0, 0, 1, 1))
                .precision();
            assertThat(empiricalPrecision).isGreaterThan(estimatedPrecision);
        }
    }

    @Test
    void inputNodesAreClampedDuringSettling() {
        var disposition = new DispositionAxes(
            "cooperative", "moderate", "calculated", "moderate", "analytical");
        AgentCapsState state = engine.initializeAgent("t1", "agent1", disposition);

        var inputs = Map.of("reward", 0.8, "punishment", 0.3);
        SettlingResult result = engine.settle(state, inputs);

        assertThat(result.activations().get("reward")).isCloseTo(0.8, within(0.001));
        assertThat(result.activations().get("punishment")).isCloseTo(0.3, within(0.001));
    }
}
