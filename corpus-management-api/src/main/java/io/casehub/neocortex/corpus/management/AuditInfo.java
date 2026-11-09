package io.casehub.neocortex.corpus.management;

import java.time.Instant;

public record AuditInfo(
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt
) {}
