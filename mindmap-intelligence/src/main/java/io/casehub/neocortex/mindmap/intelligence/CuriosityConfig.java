package io.casehub.neocortex.mindmap.intelligence;

public record CuriosityConfig(
    double proximityScale,
    long staleDaysThreshold,
    int maxBfsDepth,
    int topCentrality,
    double volatilityThreshold,
    double maxBoostFactor,
    double minDampenFactor,
    double improvingDampenCap,
    double volatilityBoostCap,
    int trajectoryLimit
) {
    public static CuriosityConfig defaults() {
        return new CuriosityConfig(7.0, 90, 4, 3, 0.3, 1.0, 0.1, 0.7, 0.5, 20);
    }
}
