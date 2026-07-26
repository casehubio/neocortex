package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CorrelationGraphTest {

    private static final CorpusRef CORPUS = new CorpusRef("t1", "c1");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant SINCE = T0.minusSeconds(1);
    private static final Instant UNTIL = T1.plusSeconds(1);

    @Test
    void emptyTrackerProducesEmptyGraph() {
        var tracker = stubTracker(List.of(), List.of());
        var graph = RetrievalAnalyzer.correlationGraph(tracker, CORPUS, SINCE, UNTIL);
        assertThat(graph.queries()).isEmpty();
        assertThat(graph.documents()).isEmpty();
    }

    @Test
    void singleQuerySingleDocument() {
        var records = List.of(record("what is X", T0, doc("doc1", 0.9)));
        var tracker = stubTracker(records, List.of());
        var graph = RetrievalAnalyzer.correlationGraph(tracker, CORPUS, SINCE, UNTIL);

        assertThat(graph.queries()).hasSize(1);
        assertThat(graph.documents()).hasSize(1);

        var qNode = graph.queries().get("what is x");
        assertThat(qNode.retrievalCount()).isEqualTo(1);
        assertThat(qNode.documentEdges()).containsKey("doc1");

        var edge = qNode.documentEdges().get("doc1");
        assertThat(edge.coOccurrenceCount()).isEqualTo(1);
        assertThat(edge.averageScore()).isEqualTo(0.9, within(0.001));
    }

    @Test
    void queryTextNormalized() {
        var records = List.of(
            record("What is X", T0, doc("doc1", 0.9)),
            record("  what is x  ", T1, doc("doc1", 0.8)));
        var tracker = stubTracker(records, List.of());
        var graph = RetrievalAnalyzer.correlationGraph(tracker, CORPUS, SINCE, UNTIL);

        assertThat(graph.queries()).hasSize(1);
        var qNode = graph.queries().get("what is x");
        assertThat(qNode.retrievalCount()).isEqualTo(2);
        assertThat(qNode.documentEdges().get("doc1").coOccurrenceCount()).isEqualTo(2);
        assertThat(qNode.documentEdges().get("doc1").averageScore())
            .isEqualTo(0.85, within(0.001));
    }

    @Test
    void feedbackAccumulatesInEdge() {
        var records = List.of(record("query", T0, doc("doc1", 0.9)));
        var feedback = List.of(
            new RetrievalFeedback("r" + T0.toEpochMilli(), "doc1", RetrievalOutcome.RELEVANT, T0),
            new RetrievalFeedback("r" + T0.toEpochMilli(), "doc1", RetrievalOutcome.HIGHLY_RELEVANT, T0));
        var tracker = stubTracker(records, feedback);
        var graph = RetrievalAnalyzer.correlationGraph(tracker, CORPUS, SINCE, UNTIL);

        var edge = graph.queries().get("query").documentEdges().get("doc1");
        assertThat(edge.outcomeDistribution())
            .containsEntry(RetrievalOutcome.RELEVANT, 1)
            .containsEntry(RetrievalOutcome.HIGHLY_RELEVANT, 1);
    }

    @Test
    void dualIndexConsistency() {
        var records = List.of(
            record("q1", T0, doc("doc1", 0.9), doc("doc2", 0.7)),
            record("q2", T1, doc("doc1", 0.8)));
        var tracker = stubTracker(records, List.of());
        var graph = RetrievalAnalyzer.correlationGraph(tracker, CORPUS, SINCE, UNTIL);

        assertThat(graph.queries()).hasSize(2);
        assertThat(graph.documents()).hasSize(2);

        var doc1 = graph.documents().get("doc1");
        assertThat(doc1.retrievalCount()).isEqualTo(2);
        assertThat(doc1.queryEdges()).containsKeys("q1", "q2");
    }

    @Test
    void edgeStatsDefensiveCopy() {
        var mutable = new HashMap<RetrievalOutcome, Integer>();
        mutable.put(RetrievalOutcome.RELEVANT, 1);
        var edge = new EdgeStats(1, 0.9, mutable);
        mutable.put(RetrievalOutcome.NOT_RELEVANT, 5);
        assertThat(edge.outcomeDistribution()).doesNotContainKey(RetrievalOutcome.NOT_RELEVANT);
    }

    @Test
    void edgeStatsValidation() {
        assertThatThrownBy(() -> new EdgeStats(0, 0.9, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EdgeStats(1, 0.9, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -- helpers --

    private static RetrievalRecord record(String queryText, Instant ts,
                                           RetrievedDocumentRef... docs) {
        return new RetrievalRecord(
            "r" + ts.toEpochMilli(), RetrievalQuery.of(queryText),
            CORPUS, List.of(docs), 10, ts);
    }

    private static RetrievedDocumentRef doc(String id, double score) {
        return new RetrievedDocumentRef(id, score);
    }

    private static RetrievalTracker stubTracker(List<RetrievalRecord> records,
                                                 List<RetrievalFeedback> feedback) {
        return new RetrievalTracker() {
            @Override public String record(RetrievalQuery q, CorpusRef c,
                    List<RetrievedChunk> r, int m) { return "stub"; }
            @Override public void feedback(String rid, String docId,
                    RetrievalOutcome o) {}
            @Override public List<RetrievalRecord> findRecords(CorpusRef c,
                    Instant s, Instant u) { return records; }
            @Override public List<RetrievalFeedback> findFeedback(CorpusRef c,
                    Instant s, Instant u) { return feedback; }
            @Override public Set<String> findRetrievedDocumentIds(CorpusRef c,
                    Instant s, Instant u) { return Set.of(); }
            @Override public int purgeOlderThan(Instant cutoff) { return 0; }
        };
    }
}
