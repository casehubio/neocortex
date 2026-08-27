package io.casehub.neocortex.mindmap.intelligence;

record ParsedContradiction(
    String entity,
    String property,
    String existing,
    String extracted,
    String explanation
) {}
