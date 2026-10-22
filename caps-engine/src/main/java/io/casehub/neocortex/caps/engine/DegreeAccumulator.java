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
    private final double slowAllowance;
    private final double slowThreshold;
    private double fastLevel;
    private double slowLevel;

    public DegreeAccumulator(double allowance, double threshold, double decay) {
        this(allowance, threshold, decay, allowance, Double.MAX_VALUE);
    }

    public DegreeAccumulator(double allowance, double threshold, double decay,
                             double slowAllowance, double slowThreshold) {
        this.allowance = allowance;
        this.threshold = threshold;
        this.decay = decay;
        this.slowAllowance = slowAllowance;
        this.slowThreshold = slowThreshold;
        this.fastLevel = 0.0;
        this.slowLevel = 0.0;
    }

    public static DegreeAccumulator withDefaults() {
        return fromConfig(DegreeAccumulatorConfig.defaults());
    }

    public static DegreeAccumulator fromConfig(DegreeAccumulatorConfig config) {
        return new DegreeAccumulator(config.allowance(), config.threshold(), config.decay(),
                config.slowAllowance(), config.slowThreshold());
    }

    /**
     * Accumulate a degree signal. Returns true if either channel crossed its
     * threshold (classification should fire). Resets both channels on firing.
     */
    public boolean accumulate(double degree) {
        fastLevel = Math.max(0.0, fastLevel * decay + degree - allowance);
        slowLevel = Math.max(0.0, slowLevel + degree - slowAllowance);

        boolean fired = fastLevel >= threshold || slowLevel >= slowThreshold;
        if (fired) {
            fastLevel = 0.0;
            slowLevel = 0.0;
        }
        return fired;
    }

    public double currentLevel() {
        return fastLevel;
    }

    public double slowLevel() {
        return slowLevel;
    }

    public void reset() {
        fastLevel = 0.0;
        slowLevel = 0.0;
    }
}
