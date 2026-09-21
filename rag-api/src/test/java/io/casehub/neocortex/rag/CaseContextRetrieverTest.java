package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaseContextRetrieverTest {

    private static final CorpusRef CORPUS_A = new CorpusRef("t1", "corpus-a");
    private static final CorpusRef CORPUS_B = new CorpusRef("t1", "corpus-b");

    private static RetrievedChunk chunk(String docId, String content, double score) {
        return new RetrievedChunk(content, docId, score, Map.of());
    }

    @Test void singleCorpusReturnsResults() {
        CaseRetriever delegate = (q, c, max, f) -> List.of(chunk("d1", "text", 0.9));
        var retriever = new CaseContextRetriever(delegate);
        var results = retriever.retrieve("query", List.of(CORPUS_A), 10);
        assertEquals(1, results.size());
        assertEquals("d1", results.getFirst().sourceDocumentId());
    }

    @Test void multiCorpusMergesResults() {
        CaseRetriever delegate = (q, c, max, f) -> {
            if (c.corpusName().equals("corpus-a")) return List.of(chunk("d1", "a", 0.9));
            return List.of(chunk("d2", "b", 0.8));
        };
        var retriever = new CaseContextRetriever(delegate);
        var results = retriever.retrieve("query", List.of(CORPUS_A, CORPUS_B), 10);
        assertEquals(2, results.size());
    }

    @Test void deduplicatesBySourceDocumentIdKeepingHighestScore() {
        CaseRetriever delegate = (q, c, max, f) -> {
            if (c.corpusName().equals("corpus-a")) return List.of(chunk("d1", "text-a", 0.7));
            return List.of(chunk("d1", "text-b", 0.9));
        };
        var retriever = new CaseContextRetriever(delegate);
        var results = retriever.retrieve("query", List.of(CORPUS_A, CORPUS_B), 10);
        assertEquals(1, results.size());
        assertEquals(0.9, results.getFirst().relevanceScore(), 0.001);
    }

    @Test void errorInOneCorpusContinuesWithOthers() {
        CaseRetriever delegate = (q, c, max, f) -> {
            if (c.corpusName().equals("corpus-a")) throw new RuntimeException("connection failed");
            return List.of(chunk("d2", "b", 0.8));
        };
        var retriever = new CaseContextRetriever(delegate);
        var results = retriever.retrieve("query", List.of(CORPUS_A, CORPUS_B), 10);
        assertEquals(1, results.size());
        assertEquals("d2", results.getFirst().sourceDocumentId());
    }

    @Test void nullQueryTextReturnsEmpty() {
        CaseRetriever delegate = (q, c, max, f) -> fail("should not be called");
        var retriever = new CaseContextRetriever(delegate);
        assertTrue(retriever.retrieve((String) null, List.of(CORPUS_A), 10).isEmpty());
    }

    @Test void blankQueryTextReturnsEmpty() {
        CaseRetriever delegate = (q, c, max, f) -> fail("should not be called");
        var retriever = new CaseContextRetriever(delegate);
        assertTrue(retriever.retrieve("  ", List.of(CORPUS_A), 10).isEmpty());
    }

    @Test void emptyCorporaListReturnsEmpty() {
        CaseRetriever delegate = (q, c, max, f) -> fail("should not be called");
        var retriever = new CaseContextRetriever(delegate);
        assertTrue(retriever.retrieve("query", List.of(), 10).isEmpty());
    }

    @Test void resultsSortedByScoreDescending() {
        CaseRetriever delegate = (q, c, max, f) -> {
            if (c.corpusName().equals("corpus-a")) return List.of(chunk("d1", "a", 0.5));
            return List.of(chunk("d2", "b", 0.9));
        };
        var retriever = new CaseContextRetriever(delegate);
        var results = retriever.retrieve("query", List.of(CORPUS_A, CORPUS_B), 10);
        assertEquals(0.9, results.get(0).relevanceScore(), 0.001);
        assertEquals(0.5, results.get(1).relevanceScore(), 0.001);
    }

    @Test void maxResultsTruncates() {
        CaseRetriever delegate = (q, c, max, f) -> List.of(
                chunk("d1", "a", 0.9), chunk("d2", "b", 0.8), chunk("d3", "c", 0.7));
        var retriever = new CaseContextRetriever(delegate);
        var results = retriever.retrieve("query", List.of(CORPUS_A), 2);
        assertEquals(2, results.size());
    }

    @Test void toMapFlattensSerialization() {
        var chunk = new RetrievedChunk("content text", "doc-42", 0.85, Map.of("source", "wiki"));
        Map<String, Object> map = CaseContextRetriever.toMap(chunk);
        assertEquals("content text", map.get("content"));
        assertEquals("doc-42", map.get("sourceDocumentId"));
        assertEquals(0.85, (double) map.get("relevanceScore"), 0.001);
        assertEquals("wiki", map.get("source"));
    }

    @Test void strategyExtractsQueryAndRetrievesResults() {
        CaseRetriever delegate = (q, c, max, f) -> {
            assertEquals("alert-rule technique-1", q.text());
            return List.of(chunk("d1", "threat intel", 0.9));
        };
        var retriever = new CaseContextRetriever(delegate);
        QueryExtractionStrategy strategy = ctx -> {
            String alert = (String) ctx.getOrDefault("alert", "");
            String technique = (String) ctx.getOrDefault("technique", "");
            return RetrievalQuery.of(alert + " " + technique);
        };
        var context = Map.<String, Object>of("alert", "alert-rule", "technique", "technique-1");
        var results = retriever.retrieve(context, strategy, List.of(CORPUS_A), 10);
        assertEquals(1, results.size());
        assertEquals("d1", results.getFirst().sourceDocumentId());
    }

    @Test void strategyReturningNullYieldsEmpty() {
        CaseRetriever delegate = (q, c, max, f) -> fail("should not be called");
        var retriever = new CaseContextRetriever(delegate);
        QueryExtractionStrategy strategy = ctx -> null;
        var results = retriever.retrieve(Map.of("alert", "test"), strategy, List.of(CORPUS_A), 10);
        assertTrue(results.isEmpty());
    }

    @Test void strategyWithEmptyCorporaReturnsEmpty() {
        CaseRetriever delegate = (q, c, max, f) -> fail("should not be called");
        var retriever = new CaseContextRetriever(delegate);
        QueryExtractionStrategy strategy = ctx -> fail("should not be called");
        var results = retriever.retrieve(Map.of(), strategy, List.of(), 10);
        assertTrue(results.isEmpty());
    }

    @Test void retrievalQueryOverloadBehavesSameAsStringOverload() {
        CaseRetriever delegate = (q, c, max, f) -> List.of(chunk("d1", "text", 0.9));
        var retriever = new CaseContextRetriever(delegate);
        var stringResults = retriever.retrieve("query", List.of(CORPUS_A), 10);
        var queryResults = retriever.retrieve(RetrievalQuery.of("query"), List.of(CORPUS_A), 10);
        assertEquals(stringResults.size(), queryResults.size());
        assertEquals(stringResults.getFirst().sourceDocumentId(), queryResults.getFirst().sourceDocumentId());
    }

    @Test void strategyCanSetWeightMultipliers() {
        CaseRetriever delegate = (q, c, max, f) -> {
            assertEquals(2.0, q.weightMultipliers().get("bm25"), 0.001);
            return List.of(chunk("d1", "text", 0.9));
        };
        var retriever = new CaseContextRetriever(delegate);
        QueryExtractionStrategy strategy = ctx ->
                RetrievalQuery.of("query").withBm25Boost(2.0);
        var results = retriever.retrieve(Map.of(), strategy, List.of(CORPUS_A), 10);
        assertEquals(1, results.size());
    }

    @Test void allCorporaFailReturnsEmpty() {
        CaseRetriever delegate = (q, c, max, f) -> { throw new RuntimeException("fail"); };
        var retriever = new CaseContextRetriever(delegate);
        var results = retriever.retrieve("query", List.of(CORPUS_A, CORPUS_B), 10);
        assertTrue(results.isEmpty());
    }
}
