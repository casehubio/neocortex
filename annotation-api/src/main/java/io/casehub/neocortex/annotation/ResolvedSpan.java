package io.casehub.neocortex.annotation;

public record ResolvedSpan(
    String entityName,
    String surfaceForm,
    int startOffset,
    int endOffset,
    int mentionIndex
) {}
