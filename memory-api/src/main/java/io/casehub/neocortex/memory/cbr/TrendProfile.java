package io.casehub.neocortex.memory.cbr;

import java.util.HashMap;
import java.util.Map;

public record TrendProfile(Map<String, Double> metrics) {
    public TrendProfile {
        metrics = Map.copyOf(metrics);
    }

    public Map<String, FeatureValue> toFeatures() {
        var result = new HashMap<String, FeatureValue>(metrics.size());
        for (var entry : metrics.entrySet()) {
            result.put(entry.getKey(), FeatureValue.number(entry.getValue()));
        }
        return Map.copyOf(result);
    }
}
