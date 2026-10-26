package io.quarkmind.inference.bayes;

import java.util.List;
import java.util.Map;

public record TrackerSnapshot(
    Map<String, Double> weights,
    Map<String, HypothesisStatus> statuses,
    String leader,
    double entropy,
    int observationCount,
    List<String> viable
) {
    public TrackerSnapshot {
        weights = Map.copyOf(weights);
        statuses = Map.copyOf(statuses);
        viable = List.copyOf(viable);
    }
}
