package io.quarkmind.inference.bayes;

import java.util.List;
import java.util.Map;

public record ObservationResult(
    Map<String, Double> weights,
    List<String> eliminated,
    String leader,
    double entropy,
    List<String> viable,
    boolean resolved
) {
    public ObservationResult {
        weights = Map.copyOf(weights);
        eliminated = List.copyOf(eliminated);
        viable = List.copyOf(viable);
    }
}
