package io.casehub.neocortex.cognition.memory;

public record RetentionConfig(double retentionThreshold,
                              double recencyWeight, double frequencyWeight) {
    public RetentionConfig {
        if (retentionThreshold < 0.0 || retentionThreshold > 1.0)
            throw new IllegalArgumentException("retentionThreshold must be in [0,1], got " + retentionThreshold);
        if (recencyWeight < 0 || frequencyWeight < 0)
            throw new IllegalArgumentException("weights must be >= 0");
        if (recencyWeight + frequencyWeight <= 0)
            throw new IllegalArgumentException("at least one weight must be > 0");
    }

    public static final RetentionConfig DEFAULT =
            new RetentionConfig(0.1, 1.0, 1.0);
}
