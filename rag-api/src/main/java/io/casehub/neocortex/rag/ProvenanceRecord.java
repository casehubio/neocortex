package io.casehub.neocortex.rag;

import java.time.Instant;
import java.util.Objects;

public record ProvenanceRecord(
    String id,
    String retrievalContext,
    String actionId,
    String actionType,
    String documentId,
    String recordedBy,
    Instant timestamp
) {
    public ProvenanceRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
