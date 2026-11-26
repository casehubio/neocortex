package io.casehub.neocortex.caps.engine;

/**
 * Configuration for CAPS weight updating during behavioral reinforcement.
 * <p>
 * Implements multi-cue Rescorla-Wagner associative learning with Pearce-Hall
 * attention modulation. Connection weights between CAPS nodes update
 * based on per-target-node prediction error: when outcomes differ from
 * ensemble prediction, weights adjust proportionally.
 *
 * @see Rescorla, R.A. & Wagner, A.R. (1972). A theory of Pavlovian
 * conditioning. Classical Conditioning II, pp. 64-99.
 * @see Pearce, J.M. & Hall, G. (1980). A model for Pavlovian learning.
 * Psychological Review, 87(6), 532-552.
 */
public record CapsWeightUpdateConfig(
        double precisionGrowthRate,
        double precisionDecayFactor,
        double confirmEpsilon,
        double minPrecision,
        double salienceAlphaScaling,
        double empiricalProvenancePrecision,
        double consensusProvenancePrecision,
        double estimatedProvenancePrecision
) {

    public double provenancePrecision(io.casehub.neocortex.caps.WeightProvenance provenance) {
        return switch (provenance) {
            case EMPIRICAL -> empiricalProvenancePrecision;
            case CONSENSUS -> consensusProvenancePrecision;
            case ESTIMATED -> estimatedProvenancePrecision;
        };
    }

    public static CapsWeightUpdateConfig defaults() {
        return new CapsWeightUpdateConfig(
                0.1, 0.9, 0.05, 0.1, 0.1, 10.0, 5.0, 1.0);
    }
}
