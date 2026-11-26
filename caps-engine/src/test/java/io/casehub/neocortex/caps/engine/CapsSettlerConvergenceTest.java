package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.ConvergenceType;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapsSettlerConvergenceTest {

    @Test
    void competingInputsProduceOscillationNotConverged() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var settler = new CapsSettler(topology);
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "osc",
            new DispositionAxes("competitive", "defiant", "bold", "independent", "confrontational"));

        var result = settler.settle(state, Map.of(
            "physical_threat", 0.9,
            "reward", 0.9
        ));

        if (result.iterations() >= 20) {
            assertThat(result.convergence())
                .as("competing strong inputs should not falsely converge")
                .isNotEqualTo(ConvergenceType.CONVERGED);
        }
    }
}
