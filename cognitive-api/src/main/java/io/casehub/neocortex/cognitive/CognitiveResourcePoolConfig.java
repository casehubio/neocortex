package io.casehub.neocortex.cognitive;

/**
 * Per-agent tuning for the shared cognitive resource pool.
 * Models Hockey's (1997) limited processing capacity — all effortful
 * cognitive activity draws from this pool, with personality-derived
 * capacity and recovery rate.
 *
 * @param maxEffort    effort units for full depletion (logarithmic scale)
 * @param recoveryRate accumulator decrement per idle-source recovery call
 */
public record CognitiveResourcePoolConfig(
        int maxEffort,
        double recoveryRate) {

    public CognitiveResourcePoolConfig {
        if (maxEffort < 1) {
            throw new IllegalArgumentException("maxEffort must be >= 1, got " + maxEffort);
        }
        if (recoveryRate < 0) {
            throw new IllegalArgumentException("recoveryRate must be >= 0, got " + recoveryRate);
        }
    }

    public static CognitiveResourcePoolConfig defaults() {
        return new CognitiveResourcePoolConfig(10, 0.5);
    }

    public CognitiveResourcePoolConfig withMaxEffort(int v) {
        return new CognitiveResourcePoolConfig(v, recoveryRate);
    }

    public CognitiveResourcePoolConfig withRecoveryRate(double v) {
        return new CognitiveResourcePoolConfig(maxEffort, v);
    }
}
