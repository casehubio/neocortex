package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class RetrievalAnalyzerQueryTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "corpus");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T01:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T02:00:00Z");
    private static final Instant SINCE = Instant.parse("2025-12-31T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-12-31T00:00:00Z");

    private static RetrievalRecord record(String queryText, Instant ts, RetrievedDocumentRef... docs) {
        return new RetrievalRecord(
            "r-" + queryText.hashCode() + "-" + ts.getEpochSecond(),
            RetrievalQuery.of(queryText), CORPUS,
            List.of(docs), Math.max(docs.length, 1), ts);
    }

    private static RetrievedDocumentRef doc(String id, double score) {
        return new RetrievedDocumentRef(id, score);
    }

    private static StubTracker tracker(RetrievalRecord... records) {
        return new StubTracker(List.of(records));
    }

    @Test
    void lowRelevanceQueries_filtersBelowThreshold() {
        var t = tracker(
            record("bad query", T0, doc("d1", 0.1), doc("d2", 0.2)),
            record("good query", T0, doc("d1", 0.9), doc("d2", 0.8)));

        var result = RetrievalAnalyzer.lowRelevanceQueries(t, CORPUS, SINCE, UNTIL, 0.5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).queryText()).isEqualTo("bad query");
        assertThat(result.get(0).averageRelevanceScore()).isCloseTo(0.15, within(0.01));
    }

    @Test
    void lowRelevanceQueries_aggregatesAcrossMultipleRetrievals() {
        var t = tracker(
            record("q", T0, doc("d1", 0.2)),
            record("q", T1, doc("d1", 0.4)));

        var result = RetrievalAnalyzer.lowRelevanceQueries(t, CORPUS, SINCE, UNTIL, 0.5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).averageRelevanceScore()).isCloseTo(0.3, within(0.01));
        assertThat(result.get(0).retrievalCount()).isEqualTo(2);
    }

    @Test
    void lowRelevanceQueries_emptyTracker_returnsEmpty() {
        var result = RetrievalAnalyzer.lowRelevanceQueries(tracker(), CORPUS, SINCE, UNTIL, 0.5);
        assertThat(result).isEmpty();
    }

    @Test
    void zeroHitQueries_findsEmptyResults() {
        var t = tracker(
            record("miss", T0),
            record("hit", T0, doc("d1", 0.9)));

        var result = RetrievalAnalyzer.zeroHitQueries(t, CORPUS, SINCE, UNTIL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).queryText()).isEqualTo("miss");
        assertThat(result.get(0).averageRelevanceScore()).isEqualTo(0.0);
    }

    @Test
    void zeroHitQueries_multipleZeroHitsForSameQuery() {
        var t = tracker(
            record("miss", T0),
            record("miss", T1));

        var result = RetrievalAnalyzer.zeroHitQueries(t, CORPUS, SINCE, UNTIL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).retrievalCount()).isEqualTo(2);
        assertThat(result.get(0).lastSeen()).isEqualTo(T1);
    }

    @Test
    void zeroHitQueries_emptyTracker_returnsEmpty() {
        var result = RetrievalAnalyzer.zeroHitQueries(tracker(), CORPUS, SINCE, UNTIL);
        assertThat(result).isEmpty();
    }

    @Test
    void queryFrequency_countsAndScores() {
        var t = tracker(
            record("popular", T0, doc("d1", 0.9)),
            record("popular", T1, doc("d2", 0.7)),
            record("rare", T2, doc("d3", 0.5)));

        var result = RetrievalAnalyzer.queryFrequency(t, CORPUS, SINCE, UNTIL);

        assertThat(result).hasSize(2);
        assertThat(result.get("popular").count()).isEqualTo(2);
        assertThat(result.get("popular").averageScore()).isCloseTo(0.8, within(0.01));
        assertThat(result.get("popular").firstSeen()).isEqualTo(T0);
        assertThat(result.get("popular").lastSeen()).isEqualTo(T1);
        assertThat(result.get("rare").count()).isEqualTo(1);
    }

    @Test
    void queryFrequency_emptyTracker_returnsEmpty() {
        var result = RetrievalAnalyzer.queryFrequency(tracker(), CORPUS, SINCE, UNTIL);
        assertThat(result).isEmpty();
    }

    private static class StubTracker implements RetrievalTracker {
        private final List<RetrievalRecord> records;

        StubTracker(List<RetrievalRecord> records) { this.records = records; }

        @Override
        public String record(RetrievalQuery query, CorpusRef corpus, List<RetrievedChunk> results, int maxResults) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void feedback(String retrievalId, String sourceDocumentId, RetrievalOutcome outcome) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RetrievalRecord> findRecords(CorpusRef corpus, Instant since, Instant until) {
            return records.stream()
                .filter(r -> r.corpus().equals(corpus))
                .filter(r -> !r.timestamp().isBefore(since) && r.timestamp().isBefore(until))
                .toList();
        }

        @Override
        public List<RetrievalFeedback> findFeedback(CorpusRef corpus, Instant since, Instant until) {
            return List.of();
        }

        @Override
        public Set<String> findRetrievedDocumentIds(CorpusRef corpus, Instant since, Instant until) {
            return Set.of();
        }

        @Override
        public int purgeOlderThan(Instant cutoff) { return 0; }
    }
}
