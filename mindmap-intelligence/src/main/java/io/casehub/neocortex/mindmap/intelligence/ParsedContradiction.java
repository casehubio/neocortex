package io.casehub.neocortex.mindmap.intelligence;

public record ParsedContradiction(
    String entity,
    String property,
    String existing,
    String extracted,
    String explanation
) {}
