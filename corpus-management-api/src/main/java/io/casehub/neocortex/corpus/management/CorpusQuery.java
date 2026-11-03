package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;

import java.util.Map;
import java.util.Set;

public record CorpusQuery(
    String text,
    PayloadFilter filter,
    Set<CorpusRef> corpusRefs,
    int maxResults,
    int federationDepth,
    boolean includeArchived,
    Map<CorpusRef, Double> corpusWeights
) {
    public static CorpusQuery of(String text) {
        return new CorpusQuery(text, null, Set.of(), 10, -1, false, Map.of());
    }

    public static CorpusQuery of(String text, int maxResults) {
        return new CorpusQuery(text, null, Set.of(), maxResults, -1, false, Map.of());
    }

    public CorpusQuery withFilter(PayloadFilter filter) {
        return new CorpusQuery(text, filter, corpusRefs, maxResults, federationDepth, includeArchived, corpusWeights);
    }

    public CorpusQuery withCorpora(Set<CorpusRef> corpora) {
        return new CorpusQuery(text, filter, corpora, maxResults, federationDepth, includeArchived, corpusWeights);
    }

    public CorpusQuery withFederationDepth(int depth) {
        return new CorpusQuery(text, filter, corpusRefs, maxResults, depth, includeArchived, corpusWeights);
    }

    public CorpusQuery withIncludeArchived(boolean include) {
        return new CorpusQuery(text, filter, corpusRefs, maxResults, federationDepth, include, corpusWeights);
    }
}
