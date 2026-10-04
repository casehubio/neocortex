package io.casehub.neocortex.knowledge;

import java.util.Objects;

public record NormalizedQuery(KnowledgeQuery query, String cacheKey) {
    public NormalizedQuery {
        Objects.requireNonNull(query);
        Objects.requireNonNull(cacheKey);
    }
}
