package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentImpactTest {

    @Test
    void rankedByCentrality() {
        var graph = graphWith(
            qNode("q1", Map.of("doc1", 0.9, "doc2", 0.7)),
            qNode("q2", Map.of("doc1", 0.8)),
            qNode("q3", Map.of("doc1", 0.85, "doc2", 0.6, "doc3", 0.5)));

        var impact = RetrievalAnalyzer.documentImpact(graph);

        assertThat(impact).hasSize(3);
        assertThat(impact.get(0).documentId()).isEqualTo("doc1");
        assertThat(impact.get(0).distinctQueryCount()).isEqualTo(3);
        assertThat(impact.get(0).totalRetrievals()).isEqualTo(3);
        assertThat(impact.get(1).documentId()).isEqualTo("doc2");
        assertThat(impact.get(1).distinctQueryCount()).isEqualTo(2);
    }

    @Test
    void outcomeAggregation() {
        var outcomes1 = Map.of(RetrievalOutcome.RELEVANT, 2);
        var outcomes2 = Map.of(RetrievalOutcome.RELEVANT, 1,
                               RetrievalOutcome.NOT_RELEVANT, 1);
        var graph = graphWith(
            qNodeWithOutcomes("q1", "doc1", 0.9, outcomes1),
            qNodeWithOutcomes("q2", "doc1", 0.8, outcomes2));

        var impact = RetrievalAnalyzer.documentImpact(graph);

        assertThat(impact.get(0).aggregateOutcomes())
            .containsEntry(RetrievalOutcome.RELEVANT, 3)
            .containsEntry(RetrievalOutcome.NOT_RELEVANT, 1);
    }

    @Test
    void emptyGraph() {
        var graph = new CorrelationGraph(Map.of(), Map.of());
        assertThat(RetrievalAnalyzer.documentImpact(graph)).isEmpty();
    }

    @Test
    void documentImpactValidation() {
        assertThatThrownBy(() -> new DocumentImpact(null, 1, 1, 0.5, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentImpact("d1", 0, 1, 0.5, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -- helpers --

    private static QueryNode qNode(String query, Map<String, Double> docs) {
        Map<String, EdgeStats> edges = new LinkedHashMap<>();
        for (var e : docs.entrySet()) {
            edges.put(e.getKey(), new EdgeStats(1, e.getValue(), Map.of()));
        }
        return new QueryNode(query, 1, edges);
    }

    private static QueryNode qNodeWithOutcomes(String query, String docId,
            double score, Map<RetrievalOutcome, Integer> outcomes) {
        return new QueryNode(query, 1,
            Map.of(docId, new EdgeStats(1, score, outcomes)));
    }

    private static CorrelationGraph graphWith(QueryNode... nodes) {
        Map<String, QueryNode> queries = new LinkedHashMap<>();
        Map<String, DocumentNode> documents = new LinkedHashMap<>();
        for (QueryNode qn : nodes) {
            queries.put(qn.queryText(), qn);
            for (var edge : qn.documentEdges().entrySet()) {
                String docId = edge.getKey();
                Map<String, EdgeStats> qEdges = new LinkedHashMap<>(
                    documents.containsKey(docId) ?
                        documents.get(docId).queryEdges() : Map.of());
                qEdges.put(qn.queryText(), edge.getValue());
                int count = qEdges.values().stream()
                    .mapToInt(EdgeStats::coOccurrenceCount).sum();
                documents.put(docId, new DocumentNode(docId, count, qEdges));
            }
        }
        return new CorrelationGraph(queries, documents);
    }
}
