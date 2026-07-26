package io.casehub.neocortex.rag;

import java.util.Map;

public record QueryNode(
    String queryText,
    int retrievalCount,
    Map<String, EdgeStats> documentEdges
) {
    public QueryNode {
        if (queryText == null || queryText.isBlank())
            throw new IllegalArgumentException("queryText must not be null or blank");
        if (retrievalCount < 1)
            throw new IllegalArgumentException("retrievalCount must be positive");
        if (documentEdges == null)
            throw new IllegalArgumentException("documentEdges must not be null");
        documentEdges = Map.copyOf(documentEdges);
    }
}
