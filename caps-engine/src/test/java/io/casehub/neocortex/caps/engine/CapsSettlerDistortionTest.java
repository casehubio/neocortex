package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapsSettlerDistortionTest {

    @Test
    void additiveDistortionRespectsNegativeActivation() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var settler = new CapsSettler(topology);
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "agent",
            new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "collaborative"));

        var resultPositive = settler.settle(state, Map.of("reward", 0.8));
        var resultNegative = settler.settle(state, Map.of("punishment", 0.8));

        double approachPos = resultPositive.activations().getOrDefault("approach", 0.0);
        double approachNeg = resultNegative.activations().getOrDefault("approach", 0.0);

        assertThat(approachPos).as("approach under reward should be positive").isGreaterThan(0.0);
        assertThat(approachNeg).as("approach under punishment should be non-positive").isLessThanOrEqualTo(0.05);
    }
}
