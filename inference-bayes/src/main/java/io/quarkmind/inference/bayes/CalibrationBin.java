package io.quarkmind.inference.bayes;

public record CalibrationBin(
    double lowerBound,
    double upperBound,
    double meanPredicted,
    double observedFrequency,
    int count
) {}
