package io.quarkmind.inference.bayes.llm;

public record ElicitorConfig(
    String modelHint,
    double temperature,
    int maxRetries
) {
    public static ElicitorConfig defaults() {
        return new ElicitorConfig(null, 0.3, 1);
    }
}
