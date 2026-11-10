package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.AgentCapsState;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.ConnectionWeight;
import io.casehub.neocortex.caps.WeightUpdateContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

/**
 * Tests for Cattell (1971) Investment Theory capacity modulation on
 * Rescorla-Wagner base alpha in {@link CapsWeightUpdater}.
 */
class CapsWeightUpdaterCapacityTest {

    private CapsWeightUpdater updater;
    private AgentCapsState baseState;
    private static final String CONNECTION_KEY = "reward__BAS_activation";

    @BeforeEach
    void setUp() {
        CapsTopology topology = new CapsTopologyLoader()
            .loadFromClasspath("caps-topology.yaml");
        updater = new CapsWeightUpdater(topology);
        baseState = new AgentCapsState("agent1", "t1", 0,
            Map.of(CONNECTION_KEY, new ConnectionWeight(0.5, 0.0, 1.0, 1.0)),
            Map.of());
    }

    @Test
    void capacityMultiplierScalesWeightDelta() {
        var inputs = Map.of("reward", 0.8);
        var ctxFull = new WeightUpdateContext(inputs, 0.8, 1.0, 0.5, "continuous", 1.0);
        var ctxHalf = new WeightUpdateContext(inputs, 0.8, 1.0, 0.5, "continuous", 0.55);

        AgentCapsState resultFull = updater.update(baseState, ctxFull);
        AgentCapsState resultHalf = updater.update(baseState, ctxHalf);

        double deltaFull = resultFull.weights().get(CONNECTION_KEY).excitatory() - 0.5;
        double deltaHalf = resultHalf.weights().get(CONNECTION_KEY).excitatory() - 0.5;

        assertThat(Math.abs(deltaHalf)).isLessThan(Math.abs(deltaFull));
        assertThat(deltaHalf / deltaFull).isCloseTo(0.55, offset(0.05));
    }

    @Test
    void capacityAtZeroProducesNoWeightChange() {
        var inputs = Map.of("reward", 0.8);
        var ctx = new WeightUpdateContext(inputs, 0.8, 1.0, 0.5, "continuous", 0.0);

        AgentCapsState result = updater.update(baseState, ctx);

        double delta = result.weights().get(CONNECTION_KEY).excitatory() - 0.5;
        assertThat(delta).isCloseTo(0.0, offset(1e-10));
    }

    @Test
    void capacityAtOneMatchesUnmodulatedBaseline() {
        var inputs = Map.of("reward", 0.8);
        var ctx = new WeightUpdateContext(inputs, 0.8, 1.0, 0.5, "continuous", 1.0);

        AgentCapsState result = updater.update(baseState, ctx);

        double delta = result.weights().get(CONNECTION_KEY).excitatory() - 0.5;
        assertThat(delta).isNotCloseTo(0.0, offset(1e-10));
    }

    @Test
    void convenienceConstructorDefaultsCapacityToOne() {
        var inputs = Map.of("reward", 0.8);
        var ctxConvenience = new WeightUpdateContext(inputs, 0.8, 1.0, 0.5, "continuous");
        var ctxExplicit = new WeightUpdateContext(inputs, 0.8, 1.0, 0.5, "continuous", 1.0);

        AgentCapsState r1 = updater.update(baseState, ctxConvenience);
        AgentCapsState r2 = updater.update(baseState, ctxExplicit);

        assertThat(r1.weights().get(CONNECTION_KEY).excitatory())
            .isEqualTo(r2.weights().get(CONNECTION_KEY).excitatory());
    }

    @Test
    void weightUpdateContextRejectsInvalidCapacity() {
        var inputs = Map.of("reward", 0.8);
        assertThatThrownBy(() -> new WeightUpdateContext(inputs, 0.8, 1.0, 0.5, "continuous", 1.5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeightUpdateContext(inputs, 0.8, 1.0, 0.5, "continuous", -0.1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
