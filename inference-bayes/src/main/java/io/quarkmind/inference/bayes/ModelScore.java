package io.quarkmind.inference.bayes;

public record ModelScore(
    double score,
    double logLikelihood,
    int freeParameters,
    int observations,
    String criterion
) {}
