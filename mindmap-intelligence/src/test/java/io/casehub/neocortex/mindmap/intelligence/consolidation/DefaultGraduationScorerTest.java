package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.GraduationContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGraduationScorerTest {

    private final DefaultGraduationScorer scorer = new DefaultGraduationScorer(3);

    private Memory memory(double confidence) {
        return new Memory("m1", Subject.of("person", "alice"),
            new MemoryDomain("experience"), "t1", null,
            "observation text", Map.of(), Instant.now(),
            Confidence.unknown(confidence), null, null, null, null, Set.of());
    }

    @Test
    void belowCorroborationThreshold_returnsZero() {
        assertEquals(0.0, scorer.score(memory(0.9),
            new GraduationContext(2, "t1")));
    }

    @Test
    void atCorroborationThreshold_returnsConfidence() {
        assertEquals(0.9, scorer.score(memory(0.9),
            new GraduationContext(3, "t1")));
    }

    @Test
    void aboveCorroborationThreshold_returnsConfidence() {
        assertEquals(0.8, scorer.score(memory(0.8),
            new GraduationContext(5, "t1")));
    }

    @Test
    void nullConfidence_returnsDefaultWhenCorroborated() {
        var mem = new Memory("m1", Subject.of("person", "alice"),
            new MemoryDomain("experience"), "t1", null,
            "text", Map.of(), Instant.now(),
            null, null, null, null, null, Set.of());
        assertEquals(0.5, scorer.score(mem, new GraduationContext(3, "t1")));
    }

    @Test
    void zeroCorroboration_returnsZero() {
        assertEquals(0.0, scorer.score(memory(0.9),
            new GraduationContext(0, "t1")));
    }
}
