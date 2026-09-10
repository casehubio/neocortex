package io.casehub.neocortex.rag;

public record AdaptiveSearchConfig(
    double scoreFloor,
    double gapThreshold,
    int minResults,
    double overfetchMultiplier
) {
    public AdaptiveSearchConfig {
        if (scoreFloor < 0 || scoreFloor > 1)
            throw new IllegalArgumentException("scoreFloor must be in [0,1]");
        if (gapThreshold < 0 || gapThreshold > 1)
            throw new IllegalArgumentException("gapThreshold must be in [0,1]");
        if (minResults < 0)
            throw new IllegalArgumentException("minResults must be >= 0");
        if (overfetchMultiplier < 1)
            throw new IllegalArgumentException("overfetchMultiplier must be >= 1");
    }

    public static AdaptiveSearchConfig defaults() {
        return new AdaptiveSearchConfig(0.3, 0.15, 3, 2.0);
    }
}
