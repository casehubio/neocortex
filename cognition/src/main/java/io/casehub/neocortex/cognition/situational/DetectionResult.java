package io.casehub.neocortex.cognition.situational;

import java.util.EnumMap;
import java.util.Map;

public record DetectionResult(
        Map<SituationalMode, Double> intensities,
        Map<SituationalMode, Boolean> engaged,
        Map<SituationalMode.Group, SituationalMode> winners,
        Map<SituationalMode, Double> exogenousEvidence,
        Map<SituationalMode, Double> endogenousEvidence,
        Map<SituationalMode, Double> relationalEvidence,
        Map<SituationalMode, Double> totalEvidence
) {
    public static DetectionResult initial() {
        var intensities = new EnumMap<SituationalMode, Double>(SituationalMode.class);
        var engaged = new EnumMap<SituationalMode, Boolean>(SituationalMode.class);
        for (SituationalMode mode : SituationalMode.values()) {
            intensities.put(mode, 0.0);
            engaged.put(mode, false);
        }
        return new DetectionResult(intensities, engaged, new EnumMap<>(SituationalMode.Group.class),
                new EnumMap<>(SituationalMode.class), new EnumMap<>(SituationalMode.class),
                new EnumMap<>(SituationalMode.class), new EnumMap<>(SituationalMode.class));
    }

    public double intensity(SituationalMode mode) {
        return intensities.getOrDefault(mode, 0.0);
    }

    public boolean isEngaged(SituationalMode mode) {
        return engaged.getOrDefault(mode, false);
    }

    public SituationalMode winner(SituationalMode.Group group) {
        return winners.get(group);
    }
}
