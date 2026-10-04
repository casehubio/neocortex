package io.casehub.neocortex.cognition.appraisal;

import java.util.Map;
import java.util.Objects;

public record PerceivedSituation(
        String narrative,
        Map<String, Double> salience) {
    public PerceivedSituation {
        Objects.requireNonNull(narrative, "narrative");
        salience = salience != null ? Map.copyOf(salience) : Map.of();
    }

    public static PerceivedSituation passThrough(String observation) {
        return new PerceivedSituation(observation, Map.of());
    }
}
