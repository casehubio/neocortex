package io.casehub.neocortex.cognition.regulation;

public record RegulationCascadeConfig(
    double regulationConfidenceThreshold,
    double regulationDominanceMargin
) {
    public RegulationCascadeConfig {
        if (regulationConfidenceThreshold < 0)
            throw new IllegalArgumentException("regulationConfidenceThreshold must be >= 0");
        if (regulationDominanceMargin < 0)
            throw new IllegalArgumentException("regulationDominanceMargin must be >= 0");
    }

    public static RegulationCascadeConfig defaults() {
        return new RegulationCascadeConfig(0.3, 0.15);
    }
}
