package io.casehub.neocortex.cognition.appraisal;

import java.util.Map;

public record HabituationConfig(
        double habituationRate,
        double noveltyThreshold,
        double repetitionTolerance,
        Map<String, Double> domainModulation) {
    public HabituationConfig {
        if (domainModulation == null) domainModulation = Map.of();
        else domainModulation = Map.copyOf(domainModulation);
    }

    public static HabituationConfig defaults() {
        return new HabituationConfig(0.2, 0.3, 5.0, Map.of());
    }
}
