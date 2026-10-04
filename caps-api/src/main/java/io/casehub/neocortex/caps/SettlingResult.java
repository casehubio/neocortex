package io.casehub.neocortex.caps;

import java.util.List;
import java.util.Map;

public record SettlingResult(
    Map<String, Double> activations,
    int iterations,
    ConvergenceType convergence,
    double saturationRatio,
    List<BehavioralAttractor> attractors
) {}
