package io.quarkmind.inference.bayes;

import java.util.LinkedHashMap;
import java.util.Map;

public record HypothesisDefinition(
    String name,
    double priorWeight,
    Map<String, String> variantActivations,
    Map<String, double[]> priorOverrides
) {
    public HypothesisDefinition {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hypothesis name must not be null or empty");
        }
        if (priorWeight <= 0) {
            throw new IllegalArgumentException("Prior weight must be positive: " + priorWeight);
        }
        variantActivations = variantActivations == null ? Map.of() : Map.copyOf(variantActivations);
        if (priorOverrides == null) {
            priorOverrides = Map.of();
        } else {
            var copy = new LinkedHashMap<String, double[]>();
            for (var entry : priorOverrides.entrySet()) {
                copy.put(entry.getKey(), entry.getValue().clone());
            }
            priorOverrides = Map.copyOf(copy);
        }
    }
}
