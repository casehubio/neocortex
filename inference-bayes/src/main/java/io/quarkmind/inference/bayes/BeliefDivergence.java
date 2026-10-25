package io.quarkmind.inference.bayes;

public record BeliefDivergence(
    String agentA,
    String agentB,
    String variable,
    double klDivergence,
    double klDivergenceReverse,
    double totalVariation
) {}
