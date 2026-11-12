package io.casehub.neocortex.cognitive;

/**
 * Per-parameter signed effects of cognitive depletion on search budget dimensions.
 * Models Hockey's (1997) compensatory control: depletion effects are
 * per-parameter and signed, enabling strategic reallocation rather than
 * uniform degradation.
 *
 * @param depthEffect       signed depletion effect on depth [-1,1]
 * @param breadthEffect     signed depletion effect on breadth [-1,1]
 * @param lateralityEffect  signed depletion effect on laterality [-1,1]
 * @param convergenceEffect signed depletion effect on convergence [-1,1]
 */
public record SearchFatigueEffects(
        double depthEffect,
        double breadthEffect,
        double lateralityEffect,
        double convergenceEffect) {

    public SearchFatigueEffects {
        validateEffect("depthEffect", depthEffect);
        validateEffect("breadthEffect", breadthEffect);
        validateEffect("lateralityEffect", lateralityEffect);
        validateEffect("convergenceEffect", convergenceEffect);
    }

    public static SearchFatigueEffects defaults() {
        return new SearchFatigueEffects(-0.1, -0.2, -0.15, 0.1);
    }

    public SearchFatigueEffects withDepthEffect(double v) {
        return new SearchFatigueEffects(v, breadthEffect, lateralityEffect, convergenceEffect);
    }

    public SearchFatigueEffects withBreadthEffect(double v) {
        return new SearchFatigueEffects(depthEffect, v, lateralityEffect, convergenceEffect);
    }

    public SearchFatigueEffects withLateralityEffect(double v) {
        return new SearchFatigueEffects(depthEffect, breadthEffect, v, convergenceEffect);
    }

    public SearchFatigueEffects withConvergenceEffect(double v) {
        return new SearchFatigueEffects(depthEffect, breadthEffect, lateralityEffect, v);
    }

    private static void validateEffect(String name, double value) {
        if (value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [-1,1], got " + value);
        }
    }
}
