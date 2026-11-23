package io.casehub.neocortex.cognition.dissonance;

public record DissonanceDetectionConfig(
    boolean enabled,
    double decayRate,
    double resolutionThreshold,
    double renderingThreshold,
    int maxRendered,
    double arousalWeight,
    double arousalCap,
    double pleasureWeight,
    double pleasureCap,
    double dominanceWeight,
    double dominanceCap,
    double copingTriggerThreshold
) {
    public static DissonanceDetectionConfig defaults() {
        return new DissonanceDetectionConfig(
            false, 0.1, 0.15, 0.3, 3,
            0.15, 0.3, 0.10, 0.2, 0.05, 0.1, 0.5);
    }
}
