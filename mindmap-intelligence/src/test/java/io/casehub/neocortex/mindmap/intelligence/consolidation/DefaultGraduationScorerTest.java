package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGraduationScorerTest {

    private final DefaultGraduationScorer scorer = new DefaultGraduationScorer();

    @Test
    void score_returnsConfidenceValue() {
        Memory memory = new Memory("m1", Subject.of("agent", "a1"),
            new MemoryDomain("experience"), "t1", null, "event",
            Map.of(), Instant.now(), Confidence.unknown(0.8),
            null, null, null, null, null);
        assertEquals(0.8, scorer.score(memory), 0.001);
    }

    @Test
    void score_returnsDefaultWhenConfidenceNull() {
        Memory memory = new Memory("m1", Subject.of("agent", "a1"),
            new MemoryDomain("experience"), "t1", null, "event",
            Map.of(), Instant.now(), null,
            null, null, null, null, null);
        assertEquals(0.5, scorer.score(memory), 0.001);
    }

    @Test
    void score_returnsValueInRange() {
        Memory memory = new Memory("m1", Subject.of("agent", "a1"),
            new MemoryDomain("experience"), "t1", null, "event",
            Map.of(), Instant.now(), Confidence.unknown(1.0),
            null, null, null, null, null);
        double score = scorer.score(memory);
        assertTrue(score >= 0.0 && score <= 1.0);
    }
}
