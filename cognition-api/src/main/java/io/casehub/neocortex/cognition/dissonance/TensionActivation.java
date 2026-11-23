package io.casehub.neocortex.cognition.dissonance;

import java.util.Map;

public record TensionActivation(
    Map<String, Double> capsActivationsA,
    Map<String, Double> capsActivationsB,
    double strength
) {
    public TensionActivation {
        capsActivationsA = capsActivationsA != null ? Map.copyOf(capsActivationsA) : Map.of();
        capsActivationsB = capsActivationsB != null ? Map.copyOf(capsActivationsB) : Map.of();
    }
}
