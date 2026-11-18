package io.casehub.neocortex.cognition.personality;

import java.time.Duration;

public record DispositionEvolutionConfig(
        Duration reflectionPeriod,
        double retentionPerReflection,
        Duration minTransitionInterval,
        int maxAuditAttempts
) {
    public DispositionEvolutionConfig {
        if (reflectionPeriod == null || reflectionPeriod.isNegative() || reflectionPeriod.isZero()) {
            throw new IllegalArgumentException("reflectionPeriod must be positive");
        }
        if (retentionPerReflection < 0 || retentionPerReflection >= 1.0) {
            throw new IllegalArgumentException("retentionPerReflection must be in [0, 1)");
        }
        if (minTransitionInterval == null || minTransitionInterval.compareTo(reflectionPeriod) < 0) {
            throw new IllegalArgumentException("minTransitionInterval must be >= reflectionPeriod");
        }
        if (maxAuditAttempts < 1) {throw new IllegalArgumentException("maxAuditAttempts must be >= 1");}
    }

    public static DispositionEvolutionConfig defaults() {
        return new DispositionEvolutionConfig(Duration.ofHours(24), 0.8, Duration.ofDays(30), 5);
    }
}
