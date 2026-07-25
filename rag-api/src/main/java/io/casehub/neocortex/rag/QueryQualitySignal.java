package io.casehub.neocortex.rag;

import java.time.Instant;

public record QueryQualitySignal(
        String queryText,
        double averageRelevanceScore,
        int retrievalCount,
        Instant lastSeen) {

    public QueryQualitySignal {
        if (queryText == null || queryText.isBlank())
            throw new IllegalArgumentException("queryText must not be null or blank");
        if (retrievalCount < 1)
            throw new IllegalArgumentException("retrievalCount must be positive");
        if (lastSeen == null)
            throw new IllegalArgumentException("lastSeen must not be null");
    }
}
