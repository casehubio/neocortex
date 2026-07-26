package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class QueryClusterTest {

    @Test
    void overlappingQueriesCluster() {
        var graph = graphWith(
            qNode("q1", "doc1", "doc2", "doc3"),
            qNode("q2", "doc1", "doc2", "doc4"));
        var clusters = RetrievalAnalyzer.queryClusters(graph, 0.4);
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).queryTexts()).containsExactlyInAnyOrder("q1", "q2");
        assertThat(clusters.get(0).sharedDocumentIds()).containsExactlyInAnyOrder("doc1", "doc2");
        assertThat(clusters.get(0).jaccardSimilarity()).isEqualTo(0.5, within(0.001));
    }

    @Test
    void disjointQueriesDoNotCluster() {
        var graph = graphWith(
            qNode("q1", "doc1"),
            qNode("q2", "doc2"));
        var clusters = RetrievalAnalyzer.queryClusters(graph, 0.1);
        assertThat(clusters).isEmpty();
    }

    @Test
    void thresholdBoundary() {
        var graph = graphWith(
            qNode("q1", "doc1", "doc2"),
            qNode("q2", "doc1", "doc3"));
        // Jaccard = 1/3 ~ 0.333
        assertThat(RetrievalAnalyzer.queryClusters(graph, 0.34)).isEmpty();
        assertThat(RetrievalAnalyzer.queryClusters(graph, 0.33)).hasSize(1);
    }

    @Test
    void transitiveClusteringMinPairwiseJaccard() {
        var graph = graphWith(
            qNode("q1", "doc1", "doc2"),
            qNode("q2", "doc1", "doc2", "doc3"),
            qNode("q3", "doc2", "doc3"));
        // J(q1,q2) = 2/3, J(q2,q3) = 2/3, J(q1,q3) = 1/3
        var clusters = RetrievalAnalyzer.queryClusters(graph, 0.5);
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).queryTexts()).containsExactlyInAnyOrder("q1", "q2", "q3");
        assertThat(clusters.get(0).jaccardSimilarity()).isLessThan(0.5);
    }

    @Test
    void singleQueryNoCluster() {
        var graph = graphWith(qNode("q1", "doc1"));
        assertThat(RetrievalAnalyzer.queryClusters(graph, 0.0)).isEmpty();
    }

    @Test
    void emptyGraphNoCluster() {
        var graph = new CorrelationGraph(Map.of(), Map.of());
        assertThat(RetrievalAnalyzer.queryClusters(graph, 0.0)).isEmpty();
    }

    @Test
    void minHashPathFindsClusterAboveThreshold() {
        // Build a graph with > MINHASH_THRESHOLD queries to force the MinHash path
        int         n     = RetrievalAnalyzer.MINHASH_THRESHOLD + 10;
        QueryNode[] nodes = new QueryNode[n];
        // First two queries share docs → should cluster
        nodes[0] = qNode("cluster-a", "shared1", "shared2", "shared3", "unique-a");
        nodes[1] = qNode("cluster-b", "shared1", "shared2", "shared3", "unique-b");
        // Remaining queries are disjoint
        for (int i = 2; i < n; i++) {
            nodes[i] = qNode("q" + i, "iso-" + i + "-1", "iso-" + i + "-2");
        }
        var graph    = graphWith(nodes);
        var clusters = RetrievalAnalyzer.queryClusters(graph, 0.5);
        assertThat(clusters).anyMatch(c ->
                                              c.queryTexts().contains("cluster-a") && c.queryTexts().contains("cluster-b"));
    }


    @Test
    void queryClusterValidation() {
        assertThatThrownBy(() -> new QueryCluster(Set.of("q1"), 0.5, Set.of("d1")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueryCluster(Set.of("q1", "q2"), -0.1, Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueryCluster(Set.of("q1", "q2"), 1.1, Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -- helpers --

    private static QueryNode qNode(String query, String... docIds) {
        Map<String, EdgeStats> edges = new LinkedHashMap<>();
        for (String docId : docIds) {
            edges.put(docId, new EdgeStats(1, 0.8, Map.of()));
        }
        return new QueryNode(query, 1, edges);
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
