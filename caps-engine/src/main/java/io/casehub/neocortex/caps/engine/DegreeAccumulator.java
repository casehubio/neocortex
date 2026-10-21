package io.casehub.neocortex.caps.engine;

/**
 * CUSUM+decay hybrid accumulator for demand-driven situation classification.
 * <p>
 * Accumulates situation-change degree signals and fires when accumulated
 * change exceeds a threshold. Combines CUSUM noise filtering with leaky
 * integrator temporal decay.
 * <p>
 * Formula: {@code S = max(0, S * λ + degree - k)}
 * <p>
 * Fires when {@code S >= h}, then resets to 0.
 *
 * @see Page, E.S. (1954). Continuous inspection schemes. Biometrika, 41(1/2), 100-115.
 * @see Usher, M. & McClelland, J.L. (2001). The time course of perceptual choice. Psychological Review, 108, 550-592.
 * @see Zacks, J.M. et al. (2007). Event perception: A mind-brain perspective. Psychological Bulletin, 133, 273-293.
 */
public class DegreeAccumulator {

    private static final double DEFAULT_ALLOWANCE = 0.2;
    private static final double DEFAULT_THRESHOLD = 1.0;
    private static final double DEFAULT_DECAY = 0.85;

    private final double allowance;
    private final double threshold;
    private final double decay;
    private double level;

    public DegreeAccumulator(double allowance, double threshold, double decay) {
        this.allowance = allowance;
        this.threshold = threshold;
        this.decay = decay;
        this.level = 0.0;
    }

    public static DegreeAccumulator withDefaults() {
        return new DegreeAccumulator(DEFAULT_ALLOWANCE, DEFAULT_THRESHOLD, DEFAULT_DECAY);
    }

    /**
     * Accumulate a degree signal. Returns true if the threshold was crossed
     * (classification should fire). Resets the accumulator on firing.
     */
    public boolean accumulate(double degree) {
        level = Math.max(0.0, level * decay + degree - allowance);
        if (level >= threshold) {
            level = 0.0;
            return true;
        }
        return false;
    }

    public double currentLevel() {
        return level;
    }

    public void reset() {
        level = 0.0;
    }
}
