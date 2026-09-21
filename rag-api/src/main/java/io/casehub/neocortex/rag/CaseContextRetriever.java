package io.casehub.neocortex.rag;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CaseContextRetriever {

    private static final Logger LOG = Logger.getLogger(CaseContextRetriever.class.getName());

    private final CaseRetriever caseRetriever;

    public CaseContextRetriever(CaseRetriever caseRetriever) {
        this.caseRetriever = caseRetriever;
    }

    public List<RetrievedChunk> retrieve(RetrievalQuery query, List<CorpusRef> corpora, int maxResults) {
        if (corpora.isEmpty()) return List.of();

        var byDocId = new LinkedHashMap<String, RetrievedChunk>();

        for (var corpus : corpora) {
            try {
                for (var chunk : caseRetriever.retrieve(query, corpus, maxResults)) {
                    byDocId.merge(chunk.sourceDocumentId(), chunk,
                            (existing, incoming) -> incoming.relevanceScore() > existing.relevanceScore() ? incoming : existing);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Retrieval from corpus " + corpus.corpusName() + " failed — skipping", e);
            }
        }

        return byDocId.values().stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::relevanceScore).reversed())
                .limit(maxResults)
                .toList();
    }

    public List<RetrievedChunk> retrieve(String queryText, List<CorpusRef> corpora, int maxResults) {
        if (queryText == null || queryText.isBlank()) return List.of();
        return retrieve(RetrievalQuery.of(queryText), corpora, maxResults);
    }

    public List<RetrievedChunk> retrieve(
            Map<String, Object> caseContext,
            QueryExtractionStrategy strategy,
            List<CorpusRef> corpora,
            int maxResults) {
        if (corpora.isEmpty()) return List.of();
        RetrievalQuery query = strategy.extractQuery(caseContext);
        if (query == null) return List.of();
        return retrieve(query, corpora, maxResults);
    }

    public static Map<String, Object> toMap(RetrievedChunk chunk) {
        var map = new LinkedHashMap<String, Object>();
        map.putAll(chunk.metadata());
        map.put("content", chunk.content());
        map.put("sourceDocumentId", chunk.sourceDocumentId());
        map.put("relevanceScore", chunk.relevanceScore());
        return Map.copyOf(map);
    }
}
