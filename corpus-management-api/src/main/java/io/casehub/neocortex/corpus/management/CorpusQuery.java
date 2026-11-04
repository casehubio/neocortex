package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;

import java.util.Map;
import java.util.Set;

public record CorpusQuery(
    String text,
    float[] embeddingVector,
    PayloadFilter filter,
    Set<CorpusRef> corpusRefs,
    int maxResults,
    int federationDepth,
    boolean includeArchived,
    Map<CorpusRef, Double> corpusWeights
) {
    public CorpusQuery {
        if (text == null && embeddingVector == null && filter == null) {
            throw new IllegalArgumentException(
                "At least one of text, embeddingVector, or filter must be non-null");
        }
    }

    public static CorpusQuery of(String text) {
        return new CorpusQuery(text, null, null, Set.of(), 10, -1, false, Map.of());
    }

    public static CorpusQuery of(String text, int maxResults) {
        return new CorpusQuery(text, null, null, Set.of(), maxResults, -1, false, Map.of());
    }

    public static CorpusQuery ofVector(float[] embeddingVector) {
        return new CorpusQuery(null, embeddingVector, null, Set.of(), 10, -1, false, Map.of());
    }

    public static CorpusQuery ofVector(float[] embeddingVector, int maxResults) {
        return new CorpusQuery(null, embeddingVector, null, Set.of(), maxResults, -1, false, Map.of());
    }

    public CorpusQuery withFilter(PayloadFilter filter) {
        return new CorpusQuery(text, embeddingVector, filter, corpusRefs, maxResults, federationDepth, includeArchived, corpusWeights);
    }

    public CorpusQuery withEmbeddingVector(float[] vector) {
        return new CorpusQuery(text, vector, filter, corpusRefs, maxResults, federationDepth, includeArchived, corpusWeights);
    }

    public CorpusQuery withCorpora(Set<CorpusRef> corpora) {
        return new CorpusQuery(text, embeddingVector, filter, corpora, maxResults, federationDepth, includeArchived, corpusWeights);
    }

    public CorpusQuery withMaxResults(int max) {
        return new CorpusQuery(text, embeddingVector, filter, corpusRefs, max, federationDepth, includeArchived, corpusWeights);
    }

    public CorpusQuery withFederationDepth(int depth) {
        return new CorpusQuery(text, embeddingVector, filter, corpusRefs, maxResults, depth, includeArchived, corpusWeights);
    }

    public CorpusQuery withIncludeArchived(boolean include) {
        return new CorpusQuery(text, embeddingVector, filter, corpusRefs, maxResults, federationDepth, include, corpusWeights);
    }

    public CorpusQuery withCorpusWeights(Map<CorpusRef, Double> weights) {
        return new CorpusQuery(text, embeddingVector, filter, corpusRefs, maxResults, federationDepth, includeArchived, weights);
    }
}
