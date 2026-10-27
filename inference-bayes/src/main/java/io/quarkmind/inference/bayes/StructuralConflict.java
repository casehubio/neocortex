package io.quarkmind.inference.bayes;

public record StructuralConflict(
    String from,
    String to,
    ConflictType type,
    String priorRationale,
    String dataRationale,
    String resolution
) {}
