package io.casehub.neocortex.rag;

import java.util.Map;
import java.util.Objects;

public record FederatedResult(
    String content,
    double adjustedScore,
    String sourceId,
    Map<String, String> metadata
) {
    public FederatedResult {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(sourceId, "sourceId");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
