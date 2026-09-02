package io.casehub.neocortex.mindmap;

import java.util.Map;

public record CuriosityConfig(
        double proximityScale,
        long staleDaysThreshold,
        int maxBfsDepth,
        int topCentrality,
        double volatilityThreshold,
        double maxBoostFactor,
        double minDampenFactor,
        double improvingDampenCap,
        double volatilityBoostCap,
        int trajectoryLimit,
        Map<String, Double> categoryWeights
) {
    public CuriosityConfig {
        categoryWeights = categoryWeights != null ? Map.copyOf(categoryWeights) : Map.of();
    }

    public double categoryWeight(String category) {
        return categoryWeights.getOrDefault(category, 1.0);
    }

    public CuriosityConfig withCategoryWeights(Map<String, Double> categoryWeights) {
        return new CuriosityConfig(proximityScale, staleDaysThreshold, maxBfsDepth, topCentrality,
                                   volatilityThreshold, maxBoostFactor, minDampenFactor, improvingDampenCap,
                                   volatilityBoostCap, trajectoryLimit, categoryWeights);
    }

    public static CuriosityConfig defaults() {
        return new CuriosityConfig(7.0, 90, 4, 3, 0.3, 1.0, 0.1, 0.7, 0.5, 20, Map.of());
    }
}
