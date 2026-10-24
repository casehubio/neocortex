package io.quarkmind.inference.bayes;

public record CompositionStats(
    int slotsUpdated,
    int slotsSkipped,
    int linksTransferred,
    long totalPropagationNanos
) {}
