package io.casehub.neocortex.memory.experience;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContentScorerTest {

    private static final ScoreableContent SAMPLE =
        new ScoreableContent("text", Map.of(), Instant.EPOCH);

    @Test
    void lambdaScorer() {
        ContentScorer scorer = c -> 0.42;
        assertEquals(0.42, scorer.score(SAMPLE), 0.001);
    }

    @Test
    void weightedCreation() {
        ContentScorer scorer = c -> 0.5;
        var weighted = ContentScorer.weighted(scorer, 0.3);
        assertEquals(0.3, weighted.weight());
        assertEquals(0.5, weighted.scorer().score(SAMPLE), 0.001);
    }

    @Test
    void weightedRejectsZeroWeight() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentScorer.weighted(c -> 0.5, 0.0));
    }

    @Test
    void weightedRejectsNegativeWeight() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentScorer.weighted(c -> 0.5, -1.0));
    }

    @Test
    void weightedRejectsNullScorer() {
        assertThrows(NullPointerException.class,
            () -> ContentScorer.weighted(null, 1.0));
    }

    @Test
    void compositeWeightedMean() {
        ContentScorer fixed80 = c -> 0.8;
        ContentScorer fixed20 = c -> 0.2;
        var composite = ContentScorer.composite(List.of(
            ContentScorer.weighted(fixed80, 0.3),
            ContentScorer.weighted(fixed20, 0.7)));
        // (0.8*0.3 + 0.2*0.7) / (0.3+0.7) = (0.24 + 0.14) / 1.0 = 0.38
        assertEquals(0.38, composite.score(SAMPLE), 0.001);
    }

    @Test
    void compositeSingleScorerReturnsItsValue() {
        ContentScorer fixed = c -> 0.42;
        var composite = ContentScorer.composite(List.of(
            ContentScorer.weighted(fixed, 1.0)));
        assertEquals(0.42, composite.score(SAMPLE), 0.001);
    }

    @Test
    void compositeClamps() {
        ContentScorer overOne = c -> 1.5;
        var composite = ContentScorer.composite(List.of(
            ContentScorer.weighted(overOne, 1.0)));
        assertEquals(1.0, composite.score(SAMPLE));
    }

    @Test
    void compositeRejectsEmpty() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentScorer.composite(List.of()));
    }
}
