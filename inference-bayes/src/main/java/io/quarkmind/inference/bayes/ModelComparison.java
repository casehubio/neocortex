package io.quarkmind.inference.bayes;

public record ModelComparison(
    ModelScore scoreA,
    ModelScore scoreB,
    String preferredModel,
    double scoreDifference
) {}
