package io.casehub.neocortex.caps.engine;

/**
 * Configuration for CAPS network settling — attractor extraction and
 * distortion thresholds during iterative activation propagation.
 *
 * The settling process propagates activations through the CAPS network
 * until convergence or oscillation, then extracts behavioral attractors
 * from output nodes. These parameters control noise filtering and
 * saturation detection during that process.
 *
 * Iteration count, convergence epsilon, and weight caps are configured
 * per-topology via {@code WeightUpdateParameters}. This config covers
 * the remaining global thresholds not tied to a specific topology.
 *
 * @see Mischel, W. & Shoda, Y. (1995). A cognitive-affective system
 *      theory of personality. Psychological Review, 102(2), 246-268.
 */
public record CapsSettlingConfig(
        /**
         * Minimum effective distortion threshold after modulation.
         *
         * Distortion definitions can modulate their base threshold based on
         * current activation polarity. This floor prevents the effective
         * threshold from reaching zero, which would cause distortions to
         * fire on arbitrarily small activations.
         *
         * Range: [0.01, 0.5]. Default 0.05 — allows distortions to become
         * very sensitive but not infinitely so.
         */
        double minEffectiveDistortionThreshold,

        /**
         * Minimum attractor strength for inclusion in settling results.
         *
         * Output nodes with activation below this threshold are not
         * reported as behavioral attractors. Filters noise from weakly
         * activated outputs that would generate meaningless behavioral
         * tendencies.
         *
         * Range: [0.01, 0.5]. Default 0.1 — consistent with typical
         * connectionist activation noise floors.
         */
        double attractorStrengthMinFilter,

        /**
         * Activation magnitude above which a mediating node is considered
         * saturated (pegged at its activation ceiling).
         *
         * High saturation ratios indicate the network has lost discriminative
         * power — most mediating nodes are at their extremes. This triggers
         * saturation warnings and modulates attractor reporting.
         *
         * Range: [0.9, 1.0]. Default 0.99 — only near-ceiling activations
         * count as saturated, following standard neural network saturation
         * definitions.
         */
        double saturationActivationThreshold
) {
    public static CapsSettlingConfig defaults() {
        return new CapsSettlingConfig(0.05, 0.1, 0.99);
    }
}
