package io.quarkmind.inference.bayes;

public record EdgeResolution(
    String from,
    String to,
    EdgeProvenance provenance,
    String rationale
) {}
