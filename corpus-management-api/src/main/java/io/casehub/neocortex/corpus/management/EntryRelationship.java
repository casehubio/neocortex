package io.casehub.neocortex.corpus.management;

import java.time.Instant;

public record EntryRelationship(
        String id,
        String fromEntryId,
        String toEntryId,
        String relationshipType,
        String createdBy,
        Instant createdAt) {}
