package io.quarkmind.inference.bayes;

public record UtilityNode(
    String name,
    String[] parents,
    double[] utilities
) {}
