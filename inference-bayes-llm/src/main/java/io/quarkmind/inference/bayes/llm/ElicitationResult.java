package io.quarkmind.inference.bayes.llm;

import io.quarkmind.inference.bayes.NetworkSpec;

import java.util.List;

public record ElicitationResult(
    NetworkSpec spec,
    List<String> warnings,
    boolean fullyParsed
) {
    public ElicitationResult {
        warnings = List.copyOf(warnings);
    }
}
