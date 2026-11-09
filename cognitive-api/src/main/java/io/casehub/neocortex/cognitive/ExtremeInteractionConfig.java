package io.casehub.neocortex.cognitive;

/**
 * Per-agent tuning for how personality modulates search budget activation
 * at extreme motivation intensity.
 * <p>
 * Decay values control the rate of exponential decline past the agent's
 * {@link SearchBudgetConfig#extremePeak()}. Zero decay means the parameter
 * is protected (no decline). Amplification rates scale drive modulations
 * past the peak: positive amplifies, negative compresses.
 *
 * @param depthDecay               exponential decay rate for depth past peak (Yerkes-Dodson)
 * @param breadthDecay             exponential decay rate for breadth past peak (Easterbrook 1959)
 * @param lateralityDecay          exponential decay rate for laterality past peak
 * @param convergenceDecay         exponential decay rate for convergence past peak
 * @param depthAmplification       drive modulation scaling rate for depth
 * @param breadthAmplification     drive modulation scaling rate for breadth
 * @param lateralityAmplification  drive modulation scaling rate for laterality
 * @param convergenceAmplification drive modulation scaling rate for convergence
 */
public record ExtremeInteractionConfig(
        double depthDecay,
        double breadthDecay,
        double lateralityDecay,
        double convergenceDecay,
        double depthAmplification,
        double breadthAmplification,
        double lateralityAmplification,
        double convergenceAmplification) {

    public ExtremeInteractionConfig {
        validateNonNegative("depthDecay", depthDecay);
        validateNonNegative("breadthDecay", breadthDecay);
        validateNonNegative("lateralityDecay", lateralityDecay);
        validateNonNegative("convergenceDecay", convergenceDecay);
    }

    /**
     * Approximates a moderate personality: conscientiousness ~0.5,
     * openness ~0.5, anxiousness ~0.3. Depth protected (decay=0),
     * breadth narrows moderately (1.5), laterality narrows faster (2.0),
     * convergence slightly compressed (0.5).
     */
    public static ExtremeInteractionConfig defaults() {
        return new ExtremeInteractionConfig(
                0.0, 1.5, 2.0, 0.5,
                0.5, -1.0, -0.8, -1.5);
    }

    public ExtremeInteractionConfig withDepthDecay(double v) {
        return new ExtremeInteractionConfig(v, breadthDecay, lateralityDecay, convergenceDecay, depthAmplification, breadthAmplification, lateralityAmplification, convergenceAmplification);
    }

    public ExtremeInteractionConfig withBreadthDecay(double v) {
        return new ExtremeInteractionConfig(depthDecay, v, lateralityDecay, convergenceDecay, depthAmplification, breadthAmplification, lateralityAmplification, convergenceAmplification);
    }

    public ExtremeInteractionConfig withLateralityDecay(double v) {
        return new ExtremeInteractionConfig(depthDecay, breadthDecay, v, convergenceDecay, depthAmplification, breadthAmplification, lateralityAmplification, convergenceAmplification);
    }

    public ExtremeInteractionConfig withConvergenceDecay(double v) {
        return new ExtremeInteractionConfig(depthDecay, breadthDecay, lateralityDecay, v, depthAmplification, breadthAmplification, lateralityAmplification, convergenceAmplification);
    }

    public ExtremeInteractionConfig withDepthAmplification(double v) {
        return new ExtremeInteractionConfig(depthDecay, breadthDecay, lateralityDecay, convergenceDecay, v, breadthAmplification, lateralityAmplification, convergenceAmplification);
    }

    public ExtremeInteractionConfig withBreadthAmplification(double v) {
        return new ExtremeInteractionConfig(depthDecay, breadthDecay, lateralityDecay, convergenceDecay, depthAmplification, v, lateralityAmplification, convergenceAmplification);
    }

    public ExtremeInteractionConfig withLateralityAmplification(double v) {
        return new ExtremeInteractionConfig(depthDecay, breadthDecay, lateralityDecay, convergenceDecay, depthAmplification, breadthAmplification, v, convergenceAmplification);
    }

    public ExtremeInteractionConfig withConvergenceAmplification(double v) {
        return new ExtremeInteractionConfig(depthDecay, breadthDecay, lateralityDecay, convergenceDecay, depthAmplification, breadthAmplification, lateralityAmplification, v);
    }

    private static void validateNonNegative(String name, double value) {
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + value);
        }
    }
}
