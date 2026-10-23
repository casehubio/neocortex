package io.quarkmind.inference.bayes;

public record EdgeSpec(
    String from,
    String to,
    String rationale,
    SpecStatus status
) {}
