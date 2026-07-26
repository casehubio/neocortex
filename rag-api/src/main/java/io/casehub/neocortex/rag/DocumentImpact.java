package io.casehub.neocortex.rag;

import java.util.Map;

public record DocumentImpact(
    String documentId,
    int distinctQueryCount,
    int totalRetrievals,
    double averageScore,
    Map<RetrievalOutcome, Integer> aggregateOutcomes
) {
    public DocumentImpact {
        if (documentId == null || documentId.isBlank())
            throw new IllegalArgumentException("documentId must not be null or blank");
        if (distinctQueryCount < 1)
            throw new IllegalArgumentException("distinctQueryCount must be positive");
        if (totalRetrievals < 1)
            throw new IllegalArgumentException("totalRetrievals must be positive");
        if (aggregateOutcomes == null)
            throw new IllegalArgumentException("aggregateOutcomes must not be null");
        aggregateOutcomes = Map.copyOf(aggregateOutcomes);
    }
}
