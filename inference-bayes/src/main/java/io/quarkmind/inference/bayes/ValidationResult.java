package io.quarkmind.inference.bayes;

import java.util.List;

public record ValidationResult(
    double meanLogLikelihood,
    double stdLogLikelihood,
    List<Double> foldScores,
    int foldCount,
    int totalObservations
) {
    public ValidationResult {
        foldScores = List.copyOf(foldScores);
    }
}
