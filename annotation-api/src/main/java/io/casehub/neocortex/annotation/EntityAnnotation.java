package io.casehub.neocortex.annotation;

import java.time.Instant;
import java.util.Objects;

public record EntityAnnotation(
    String annotationId,
    String tenantId,
    String sourceType,
    String sourceId,
    String entityNodeId,
    int startOffset,
    int endOffset,
    String mentionText,
    Double confidence,
    String method,
    String ownerSubjectId,
    Instant createdAt
) {
    public EntityAnnotation {
        Objects.requireNonNull(annotationId, "annotationId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(entityNodeId, "entityNodeId");
        Objects.requireNonNull(mentionText, "mentionText");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(createdAt, "createdAt");
        if (startOffset < 0) throw new IllegalArgumentException("startOffset must be >= 0");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset must be > startOffset");
    }
}
