package io.casehub.neocortex.rag;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record FederationQuery(
    String localId,
    String queryText,
    int maxResults,
    Set<String> visited,
    Map<String, List<String>> filterContext
) {
    public FederationQuery {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(queryText, "queryText");
        visited = Set.copyOf(visited);
        filterContext = filterContext != null
            ? Map.copyOf(filterContext)
            : Map.of();
    }
}
