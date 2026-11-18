package io.casehub.neocortex.cognition.situational;

import io.casehub.eidos.api.SituationalProfile;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SituationalDeltaConverter {

    private SituationalDeltaConverter() {}

    public static Map<SituationalMode, Map<String, Double>> toSituationalDeltas(List<SituationalProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return Map.of();

        var result = new EnumMap<SituationalMode, Map<String, Double>>(SituationalMode.class);
        for (var profile : profiles) {
            var mode = toMode(profile.mode());
            if (mode == null) continue;

            var deltas = new LinkedHashMap<String, Double>();
            for (var shift : profile.shifts()) {
                deltas.put(shift.facet(), shift.delta() / 100.0);
            }
            result.put(mode, Map.copyOf(deltas));
        }
        return Map.copyOf(result);
    }

    static SituationalMode toMode(String eidosMode) {
        if (eidosMode == null) return null;
        try {
            return SituationalMode.valueOf(eidosMode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
