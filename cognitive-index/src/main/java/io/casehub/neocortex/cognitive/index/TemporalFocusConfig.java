package io.casehub.neocortex.cognitive.index;

import java.util.Map;

public record TemporalFocusConfig(
        double proximityScale,
        double worseningBoostCap,
        double improvingDampenFactor,
        double volatilityBoostCap,
        Map<String, Double> subgraphProximityWeights
) {
    public TemporalFocusConfig {
        subgraphProximityWeights = subgraphProximityWeights != null ? Map.copyOf(subgraphProximityWeights) : Map.of();
    }

    public double subgraphProximityWeight(String subgraphType) {
        return subgraphProximityWeights.getOrDefault(subgraphType, 1.0);
    }

    public TemporalFocusConfig withSubgraphProximityWeights(Map<String, Double> subgraphProximityWeights) {
        return new TemporalFocusConfig(proximityScale, worseningBoostCap, improvingDampenFactor,
                                       volatilityBoostCap, subgraphProximityWeights);
    }

    public static TemporalFocusConfig defaults() {
        return new TemporalFocusConfig(7.0, 1.0, 0.5, 0.5, Map.of());
    }
}
