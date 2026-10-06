package io.casehub.neocortex.knowledge.cache;

import io.casehub.neocortex.knowledge.ExpandedTerm;
import io.casehub.neocortex.knowledge.NormalizedQuery;

import java.util.Map;
import java.util.Objects;

public record NormalizationResult(NormalizedQuery normalizedQuery,
                                  Map<String, ExpandedTerm> expansions) {
    public NormalizationResult {
        Objects.requireNonNull(normalizedQuery);
        expansions = Map.copyOf(expansions);
    }
}
