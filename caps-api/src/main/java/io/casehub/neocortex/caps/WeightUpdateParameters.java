package io.casehub.neocortex.caps;

import java.util.Map;

public record WeightUpdateParameters(
    double alphaMin,
    double alphaMax,
    double betaMin,
    double betaMax,
    double temporalDiscount,
    Map<String, Double> scheduleModifiers,
    double vicariousDiscount,
    int maxIterations,
    double epsilon,
    double[] effectiveWeightCap,
    double[] compoundDistortionCap,
    double saturationWarningThreshold
) {}
