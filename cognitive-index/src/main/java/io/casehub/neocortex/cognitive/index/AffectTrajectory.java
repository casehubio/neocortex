package io.casehub.neocortex.cognitive.index;

/**
 * Computed affect trajectory metrics from a series of PAD observations.
 * This is a derived view — stateless computation over domain="affect"
 * memories, not a persistent data structure.
 *
 * @param pleasureSlope least-squares slope of pleasure over time (positive = improving)
 * @param arousalVolatility standard deviation of arousal values
 * @param dominanceSlope least-squares slope of dominance over time
 * @param trend overall direction based on pleasure slope
 * @param rateOfChange magnitude of the pleasure slope (absolute value)
 * @param sampleCount number of observations used
 */
public record AffectTrajectory(
    double pleasureSlope,
    double arousalVolatility,
    double dominanceSlope,
    TrendDirection trend,
    double rateOfChange,
    int sampleCount
) {}
