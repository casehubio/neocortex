package io.casehub.neocortex.rag;

import java.util.Map;

public record CorrelationGraph(
    Map<String, QueryNode> queries,
    Map<String, DocumentNode> documents
) {
    public CorrelationGraph {
        if (queries == null)
            throw new IllegalArgumentException("queries must not be null");
        if (documents == null)
            throw new IllegalArgumentException("documents must not be null");
        queries = Map.copyOf(queries);
        documents = Map.copyOf(documents);
    }
}
