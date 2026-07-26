package io.casehub.neocortex.rag;

import java.util.Map;

public record DocumentNode(
    String documentId,
    int retrievalCount,
    Map<String, EdgeStats> queryEdges
) {
    public DocumentNode {
        if (documentId == null || documentId.isBlank())
            throw new IllegalArgumentException("documentId must not be null or blank");
        if (retrievalCount < 1)
            throw new IllegalArgumentException("retrievalCount must be positive");
        if (queryEdges == null)
            throw new IllegalArgumentException("queryEdges must not be null");
        queryEdges = Map.copyOf(queryEdges);
    }
}
