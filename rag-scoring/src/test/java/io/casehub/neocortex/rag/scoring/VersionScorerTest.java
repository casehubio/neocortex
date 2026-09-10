package io.casehub.neocortex.rag.scoring;

import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoringContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VersionScorerTest {

    private static final VersionScorer.Config CONFIG = new VersionScorer.Config(0.1, 0.3, 0.5);
    private final VersionScorer scorer = new VersionScorer("verified_on", CONFIG);

    private static RetrievedChunk chunk(String verifiedOn) {
        var meta = verifiedOn != null ? Map.of("verified_on", verifiedOn) : Map.<String, String>of();
        return new RetrievedChunk("content", "doc1", 0.9, meta);
    }

    @Test
    void exactMatch_returnsOne() {
        var ctx = new ScoringContext(Map.of("quarkus", "3.21"));
        double score = scorer.adjust(chunk("quarkus:3.21"), RetrievalQuery.of("query"), ctx);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void minorMismatch_smallPenalty() {
        var ctx = new ScoringContext(Map.of("quarkus", "3.21"));
        double score = scorer.adjust(chunk("quarkus:3.19"), RetrievalQuery.of("query"), ctx);
        assertThat(score).isLessThan(1.0).isGreaterThan(CONFIG.floor());
    }

    @Test
    void majorMismatch_returnsFloor() {
        var ctx = new ScoringContext(Map.of("quarkus", "4.0"));
        double score = scorer.adjust(chunk("quarkus:3.21"), RetrievalQuery.of("query"), ctx);
        assertThat(score).isEqualTo(CONFIG.floor());
    }

    @Test
    void missingVersion_returnsOne() {
        var ctx = new ScoringContext(Map.of("quarkus", "3.21"));
        double score = scorer.adjust(chunk(null), RetrievalQuery.of("query"), ctx);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void emptyBom_returnsOne() {
        double score = scorer.adjust(chunk("quarkus:3.21"), RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void stackNotInBom_returnsOne() {
        var ctx = new ScoringContext(Map.of("java", "21"));
        double score = scorer.adjust(chunk("quarkus:3.21"), RetrievalQuery.of("query"), ctx);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void topicInQuery_fullWeight() {
        var ctx = new ScoringContext(Map.of("quarkus", "3.21"));
        double withTopic = scorer.adjust(chunk("quarkus:3.19"), RetrievalQuery.of("quarkus config"), ctx);
        double withoutTopic = scorer.adjust(chunk("quarkus:3.19"), RetrievalQuery.of("database setup"), ctx);
        assertThat(withTopic).isLessThanOrEqualTo(withoutTopic);
    }

    @Test
    void invalidFormat_returnsOne() {
        var ctx = new ScoringContext(Map.of("quarkus", "3.21"));
        double score = scorer.adjust(chunk("nocolon"), RetrievalQuery.of("query"), ctx);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void queryContainsStack_detects() {
        assertThat(VersionScorer.queryContainsStack("quarkus config issue", "quarkus")).isTrue();
        assertThat(VersionScorer.queryContainsStack("database setup", "quarkus")).isFalse();
        assertThat(VersionScorer.queryContainsStack(null, "quarkus")).isFalse();
        assertThat(VersionScorer.queryContainsStack("", "quarkus")).isFalse();
    }

    @Test
    void parseVersion_handlesVariousFormats() {
        assertThat(VersionScorer.parseVersion("3.21")).isEqualTo(new int[]{3, 21});
        assertThat(VersionScorer.parseVersion("3")).isEqualTo(new int[]{3, 0});
        assertThat(VersionScorer.parseVersion("3.21.1")).isEqualTo(new int[]{3, 21});
        assertThat(VersionScorer.parseVersion("abc")).isEqualTo(new int[]{0, 0});
    }

    @Test
    void scoreNeverBelowFloor() {
        var ctx = new ScoringContext(Map.of("quarkus", "3.50"));
        double score = scorer.adjust(chunk("quarkus:3.0"), RetrievalQuery.of("quarkus"), ctx);
        assertThat(score).isGreaterThanOrEqualTo(CONFIG.floor());
    }
}
