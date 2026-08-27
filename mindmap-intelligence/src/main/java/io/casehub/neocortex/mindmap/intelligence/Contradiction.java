package io.casehub.neocortex.mindmap.intelligence;

public record Contradiction(
    String entityName,
    String property,
    String existingValue,
    String extractedValue,
    String description
) {}
