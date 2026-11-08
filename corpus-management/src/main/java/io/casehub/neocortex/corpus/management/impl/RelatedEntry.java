package io.casehub.neocortex.corpus.management.impl;

import java.time.Instant;

public record RelatedEntry(
        String entryId,
        String relationshipType,
        String direction,
        Instant createdAt) {

    private static final java.util.Map<String, String> INVERSE_TYPES = java.util.Map.of(
            "supersedes", "superseded-by",
            "updates", "updated-by",
            "derived-from", "source-of");

    public static RelatedEntry forward(String entryId, String type, Instant createdAt) {
        return new RelatedEntry(entryId, type, "forward", createdAt);
    }

    public static RelatedEntry inverse(String entryId, String type, Instant createdAt) {
        String inverseType = INVERSE_TYPES.getOrDefault(type, type);
        return new RelatedEntry(entryId, inverseType, "inverse", createdAt);
    }
}
