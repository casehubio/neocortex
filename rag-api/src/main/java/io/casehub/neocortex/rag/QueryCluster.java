package io.casehub.neocortex.rag;

import java.util.Set;

public record QueryCluster(
    Set<String> queryTexts,
    double jaccardSimilarity,
    Set<String> sharedDocumentIds
) {
    public QueryCluster {
        if (queryTexts == null || queryTexts.size() < 2)
            throw new IllegalArgumentException("queryTexts must contain at least 2 queries");
        if (jaccardSimilarity < 0.0 || jaccardSimilarity > 1.0)
            throw new IllegalArgumentException("jaccardSimilarity must be in [0, 1]");
        if (sharedDocumentIds == null)
            throw new IllegalArgumentException("sharedDocumentIds must not be null");
        queryTexts = Set.copyOf(queryTexts);
        sharedDocumentIds = Set.copyOf(sharedDocumentIds);
    }
}
