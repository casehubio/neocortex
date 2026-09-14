package io.casehub.neocortex.cognitive.observability;

import java.util.List;

public record TraceEvent(
    GraphMutation mutation,
    Type type,
    List<String> relatedEntities
) {
    public enum Type {
        CREATED,
        UPDATED,
        ERASED,
        MERGED_INTO,
        MERGED_FROM,
        SUPERSEDED,
        SUPERSEDED_BY,
        REINSTATED,
        ALIAS_ADDED,
        ALIAS_REMOVED
    }
}
