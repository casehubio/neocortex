package io.casehub.neocortex.rag.scoring;

import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoringContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TemporalDecayScorerTest {

    private static final Map<Integer, Duration> TIER_HALF_LIVES = Map.of(
        0, Duration.ofDays(30),
        2, Duration.ofDays(365)
    );
    private static final Duration DEFAULT_HALF_LIFE = Duration.ofDays(90);

    private final TemporalDecayScorer scorer = new TemporalDecayScorer(
        "submitted", "decay_tier", TIER_HALF_LIVES, DEFAULT_HALF_LIFE);

    private static RetrievedChunk chunk(String date, String tier) {
        var meta = new java.util.HashMap<String, String>();
        if (date != null) meta.put("submitted", date);
        if (tier != null) meta.put("decay_tier", tier);
        return new RetrievedChunk("content", "doc1", 0.9, Map.copyOf(meta));
    }

    @Test
    void freshDocument_returnsOne() {
        var chunk = chunk(LocalDate.now().toString(), "1");
        double score = scorer.adjust(chunk, RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void oldDocument_defaultTier_decays() {
        var chunk = chunk(LocalDate.now().minusDays(90).toString(), "1");
        double score = scorer.adjust(chunk, RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isCloseTo(0.5, within(0.01));
    }

    @Test
    void tier0_decaysFaster() {
        var chunk = chunk(LocalDate.now().minusDays(30).toString(), "0");
        double score = scorer.adjust(chunk, RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isCloseTo(0.5, within(0.01));
    }

    @Test
    void tier2_decaysSlower() {
        var chunk = chunk(LocalDate.now().minusDays(365).toString(), "2");
        double score = scorer.adjust(chunk, RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isCloseTo(0.5, within(0.01));
    }

    @Test
    void missingDate_returnsOne() {
        var chunk = chunk(null, "1");
        double score = scorer.adjust(chunk, RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void missingTier_usesDefault() {
        var chunk = chunk(LocalDate.now().minusDays(90).toString(), null);
        double score = scorer.adjust(chunk, RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isCloseTo(0.5, within(0.01));
    }

    @Test
    void invalidDate_returnsOne() {
        var chunk = chunk("not-a-date", "1");
        double score = scorer.adjust(chunk, RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void futureDate_returnsOne() {
        var chunk = chunk(LocalDate.now().plusDays(10).toString(), "1");
        double score = scorer.adjust(chunk, RetrievalQuery.of("query"), ScoringContext.EMPTY);
        assertThat(score).isEqualTo(1.0);
    }
}
