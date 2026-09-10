package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.time.Duration;
import java.time.Instant;

public final class RetrievalStrength {

    private RetrievalStrength() {}

    public static double compute(Instant lastAccessed, int storageStrength,
                                  double baseHalfLifeDays) {
        if (lastAccessed == null) return 0.0;
        double hoursSince = Duration.between(lastAccessed, Instant.now()).toHours();
        if (hoursSince <= 0) return 1.0;
        double effectiveHalfLifeHours = baseHalfLifeDays * 24.0
            * (1 + Math.log1p(storageStrength));
        return Math.pow(2.0, -hoursSince / effectiveHalfLifeHours);
    }
}
