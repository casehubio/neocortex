package io.casehub.neocortex.caps.engine;

/**
 * Configuration for CAPS weight updating during behavioral reinforcement.
 *
 * Implements Rescorla-Wagner associative learning with Pearce-Hall
 * attention modulation. Connection weights between CAPS nodes update
 * based on prediction error: when outcomes differ from expectations,
 * weights adjust proportionally.
 *
 * The precision mechanism (Pearce-Hall) modulates learning rate based
 * on prediction accuracy — surprising outcomes increase attention
 * (higher learning rate), predictable outcomes decrease it.
 *
 * Provenance precision reflects epistemic confidence: empirically
 * validated weights resist change more than estimated ones, following
 * Bayesian credible-interval logic (Kruschke, 2015, Doing Bayesian
 * Data Analysis).
 *
 * @see Rescorla, R.A. & Wagner, A.R. (1972). A theory of Pavlovian
 *      conditioning. Classical Conditioning II, pp. 64-99.
 * @see Pearce, J.M. & Hall, G. (1980). A model for Pavlovian learning.
 *      Psychological Review, 87(6), 532-552.
 */
public record CapsWeightUpdateConfig(
        /**
         * Rate at which prediction precision grows after confirmed predictions.
         *
         * When the network correctly predicts an outcome, precision increases
         * by this amount, reducing future learning rate (Pearce-Hall attention
         * decay). Higher values mean the network becomes "sure of itself"
         * faster, reducing sensitivity to new evidence.
         *
         * Range: [0.0, 1.0]. Default 0.1 based on typical Pearce-Hall
         * attention decay rates in animal conditioning studies.
         *
         * @see Pearce & Hall (1980), Eq. 3: alpha(n+1) = |lambda - V(n)|
         */
        double precisionGrowthRate,

        /**
         * Factor by which precision decays after a prediction error.
         *
         * When the prediction is wrong, precision is multiplied by this factor,
         * increasing future learning rate. Values close to 1.0 produce slow
         * precision decay (stubborn network); lower values produce rapid
         * reopening to learning.
         *
         * Range: [0.0, 1.0]. Default 0.9 — moderate decay, consistent with
         * Pearce-Hall attention recovery rates.
         */
        double precisionDecayFactor,

        /**
         * Threshold below which a prediction error is treated as confirmation.
         *
         * If |predictionError| < confirmEpsilon, the outcome is considered
         * correctly predicted and precision grows rather than decays. Acts as
         * a noise floor to prevent minor fluctuations from resetting precision.
         *
         * Range: [0.0, 0.5]. Default 0.05 — permits small prediction
         * errors without triggering relearning.
         */
        double confirmEpsilon,

        /**
         * Base rate of inhibitory weight decay per update cycle.
         *
         * Inhibitory connections decay toward zero at this rate, modulated by
         * the connection's decay resistance. Models extinction of learned
         * avoidance/suppression patterns over time.
         *
         * Range: [0.0, 1.0]. Default 0.05 — slow extinction, consistent
         * with empirical extinction rates in Pavlovian paradigms
         * (Bouton, 2004, Learning & Memory).
         */
        double baseDecayRate,

        /**
         * Floor for precision values — prevents precision from reaching zero.
         *
         * Even highly uncertain connections retain a minimum precision, ensuring
         * learning rate cannot become infinite. This bounds the Pearce-Hall
         * alpha parameter.
         *
         * Range: [0.01, 1.0]. Default 0.1.
         */
        double minPrecision,

        /**
         * Scaling factor from salience multiplier to alpha (learning rate).
         *
         * Maps the input salience signal to a base learning rate before
         * precision modulation. The effective alpha = salience * this factor,
         * clamped to [alphaMin, alphaMax].
         *
         * Range: [0.01, 1.0]. Default 0.1 — salience of 1.0 produces
         * alpha=0.1, a moderate base learning rate.
         */
        double salienceAlphaScaling,

        /**
         * Dampening factor for decay resistance on effective weight computation.
         *
         * Decay resistance > 1.0 protects a connection from inhibitory decay.
         * This factor controls how strongly resistance moderates the decay:
         * effectiveResistance = 1.0 + (resistance - 1.0) * dampening.
         *
         * Range: [0.0, 1.0]. Default 0.5 — resistance has half its
         * raw effect, preventing extreme decay immunity.
         */
        double resistanceDampening,

        /**
         * Precision assigned to empirically validated weight provenance.
         *
         * Weights with EMPIRICAL provenance (measured from real behavioral data)
         * are assigned high precision, making them resistant to learning-driven
         * change. CONSENSUS and ESTIMATED weights have progressively lower
         * precision, allowing faster adaptation.
         *
         * Range: [1.0, 100.0]. Default 10.0 — empirical weights are 10x
         * more resistant to change than estimated ones (precision=1.0).
         */
        double empiricalProvenancePrecision,

        /**
         * Precision assigned to consensus-derived weight provenance.
         *
         * Range: [1.0, 50.0]. Default 5.0.
         */
        double consensusProvenancePrecision,

        /**
         * Precision assigned to estimated weight provenance.
         *
         * Range: [0.1, 10.0]. Default 1.0 — no precision protection,
         * fully open to learning.
         */
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
                0.1, 0.9, 0.05, 0.05, 0.1, 0.1, 0.5, 10.0, 5.0, 1.0);
    }
}
