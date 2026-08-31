package io.casehub.neocortex.cognitive.index;

public record TemporalFocusConfig(
    double proximityScale,
    double worseningBoostCap,
    double improvingDampenFactor,
    double volatilityBoostCap
) {
    public static TemporalFocusConfig defaults() {
        return new TemporalFocusConfig(7.0, 1.0, 0.5, 0.5);
    }
}
