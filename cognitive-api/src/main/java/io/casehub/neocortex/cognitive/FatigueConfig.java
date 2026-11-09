package io.casehub.neocortex.cognitive;

/**
 * Per-agent tuning for fatigue dynamics under sustained extreme motivation.
 * Models Hockey's (1997) compensatory control: fatigue effects are
 * per-parameter and signed, enabling strategic reallocation rather than
 * uniform depletion.
 *
 * @param maxTicks         ticks at extreme intensity for full fatigue (logarithmic scale)
 * @param recoveryRate     fatigue accumulator decrement per sub-peak tick
 * @param depthEffect      signed fatigue effect on depth [-1,1]
 * @param breadthEffect    signed fatigue effect on breadth [-1,1]
 * @param lateralityEffect signed fatigue effect on laterality [-1,1]
 * @param convergenceEffect signed fatigue effect on convergence [-1,1]
 */
public record FatigueConfig(
        int maxTicks,
        double recoveryRate,
        double depthEffect,
        double breadthEffect,
        double lateralityEffect,
        double convergenceEffect) {

    public FatigueConfig {
        if (maxTicks < 1) {
            throw new IllegalArgumentException("maxTicks must be >= 1, got " + maxTicks);
        }
        if (recoveryRate < 0) {
            throw new IllegalArgumentException("recoveryRate must be >= 0, got " + recoveryRate);
        }
        validateEffect("depthEffect", depthEffect);
        validateEffect("breadthEffect", breadthEffect);
        validateEffect("lateralityEffect", lateralityEffect);
        validateEffect("convergenceEffect", convergenceEffect);
    }

    public static FatigueConfig defaults() {
        return new FatigueConfig(10, 0.5, -0.1, -0.2, -0.15, 0.1);
    }

    public FatigueConfig withMaxTicks(int v) {
        return new FatigueConfig(v, recoveryRate, depthEffect, breadthEffect, lateralityEffect, convergenceEffect);
    }

    public FatigueConfig withRecoveryRate(double v) {
        return new FatigueConfig(maxTicks, v, depthEffect, breadthEffect, lateralityEffect, convergenceEffect);
    }

    public FatigueConfig withDepthEffect(double v) {
        return new FatigueConfig(maxTicks, recoveryRate, v, breadthEffect, lateralityEffect, convergenceEffect);
    }

    public FatigueConfig withBreadthEffect(double v) {
        return new FatigueConfig(maxTicks, recoveryRate, depthEffect, v, lateralityEffect, convergenceEffect);
    }

    public FatigueConfig withLateralityEffect(double v) {
        return new FatigueConfig(maxTicks, recoveryRate, depthEffect, breadthEffect, v, convergenceEffect);
    }

    public FatigueConfig withConvergenceEffect(double v) {
        return new FatigueConfig(maxTicks, recoveryRate, depthEffect, breadthEffect, lateralityEffect, v);
    }

    private static void validateEffect(String name, double value) {
        if (value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [-1,1], got " + value);
        }
    }
}
