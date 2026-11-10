package io.casehub.neocortex.caps;

import java.util.Map;
import java.util.Objects;

/**
 * Context for CAPS weight updates. Replaces the 6-parameter {@link CapsEngine#updateWeights}
 * signature with a single context record for extensibility.
 *
 * <p>{@code capacityMultiplier} implements Cattell's (1971) Investment Theory:
 * {@code associativeReach} (Mednick 1962) modulates the Rescorla-Wagner (1972)
 * base alpha &mdash; higher associative capacity strengthens stimulus-response
 * learning speed. Sourced from
 * {@link io.casehub.neocortex.cognitive.CognitiveCapacityConfig#associativeReachOrDefault()}.
 *
 * @param capacityMultiplier cognitive capacity scaling factor in [0,1]; 1.0 means uncapped
 */
public record WeightUpdateContext(
        Map<String, Double> inputActivations,
        double outcomeIntensity,
        double outcomeValence,
        double salienceMultiplier,
        String reinforcementSchedule,
        double capacityMultiplier
) {
    public WeightUpdateContext {
        Objects.requireNonNull(inputActivations);
        Objects.requireNonNull(reinforcementSchedule);
        if (capacityMultiplier < 0.0 || capacityMultiplier > 1.0) {
            throw new IllegalArgumentException("capacityMultiplier must be in [0,1], got " + capacityMultiplier);
        }
    }

    /**
     * Convenience constructor defaulting {@code capacityMultiplier} to 1.0 (uncapped).
     */
    public WeightUpdateContext(Map<String, Double> inputActivations,
                               double outcomeIntensity, double outcomeValence,
                               double salienceMultiplier, String reinforcementSchedule) {
        this(inputActivations, outcomeIntensity, outcomeValence,
             salienceMultiplier, reinforcementSchedule, 1.0);
    }
}
