package io.quarkmind.inference.bayes;

public record BayesStats(
        int dirtyVariableCount,
        long lastPropagationNanos,
        int retractionCount,
        int totalCliqueCount,
        int maxCliqueSize,
        int evidenceVariableCount
) {
}
