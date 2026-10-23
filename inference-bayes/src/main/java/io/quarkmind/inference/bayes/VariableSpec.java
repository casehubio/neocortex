package io.quarkmind.inference.bayes;

import java.util.List;

public record VariableSpec(
        String name,
        List<String> outcomes,
        String description,
        SpecStatus status,
        CptSpec cptSpec
) {
    public VariableSpec {
        outcomes = List.copyOf(outcomes);
    }
}
