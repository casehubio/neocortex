package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.ModulationProfile;
import io.casehub.neocortex.memory.Memory;

public class ImportanceModulationFactor implements ModulationFactor<Memory> {

    static final String ATTR_KEY = "importance";
    private static final double DEFAULT_IMPORTANCE = 1.0;

    @Override
    public double apply(Memory item, ModulationProfile<Memory> profile) {
        String raw = item.attributes().get(ATTR_KEY);
        if (raw == null) return DEFAULT_IMPORTANCE;
        try {
            int score = Integer.parseInt(raw);
            return Math.max(0.1, score / 10.0);
        } catch (NumberFormatException e) {
            return DEFAULT_IMPORTANCE;
        }
    }
}
