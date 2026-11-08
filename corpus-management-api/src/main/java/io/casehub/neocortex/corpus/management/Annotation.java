package io.casehub.neocortex.corpus.management;

import java.time.Instant;

public record Annotation(
        String id,
        String entryId,
        String note,
        String annotationType,
        String annotatedBy,
        Instant createdAt) {}
