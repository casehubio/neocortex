package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalRankerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Confidence CONF = new Confidence(ConfidenceOrigin.STATED, 0.9, NOW);

    @Test
    void recency_newerScoresHigher() {
        var ranker = TemporalRanker.recency();
        var recent = new TemporalEntry(NOW.minusSeconds(60), new TemporalSource.FromMemory(null), "t1", CONF);
        var old = new TemporalEntry(NOW.minusSeconds(3600), new TemporalSource.FromMemory(null), "t1", CONF);

        assertThat(ranker.score(recent, NOW)).isGreaterThan(ranker.score(old, NOW));
    }

    @Test
    void recency_approachingFutureScoresHigher() {
        var ranker = TemporalRanker.recency();
        var soon = new TemporalEntry(NOW.plusSeconds(60), new TemporalSource.FromMemory(null), "t1", CONF);
        var far = new TemporalEntry(NOW.plusSeconds(3600), new TemporalSource.FromMemory(null), "t1", CONF);

        assertThat(ranker.score(soon, NOW)).isGreaterThan(ranker.score(far, NOW));
    }

    @Test
    void rank_reordersByScoreDescending() {
        var ranker = TemporalRanker.recency();
        var old = new TemporalEntry(NOW.minusSeconds(3600), new TemporalSource.FromMemory(null), "t1", CONF);
        var recent = new TemporalEntry(NOW.minusSeconds(60), new TemporalSource.FromMemory(null), "t1", CONF);

        var ranked = ranker.rank(List.of(old, recent), NOW);
        assertThat(ranked).containsExactly(recent, old);
    }
}
