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

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void textSimilarityAboveThreshold_returnsConfidence() {
        assertEquals(0.9, scorer.score(memory(0.9),
                                       new GraduationContext(1, 4, "t1")));
    }

    @Test
    void textSimilarityBelowThreshold_returnsZero() {
        assertEquals(0.0, scorer.score(memory(0.9),
                                       new GraduationContext(1, 2, "t1")));
    }

    @Test
    void bothBelowThreshold_returnsZero() {
        assertEquals(0.0, scorer.score(memory(0.9),
                                       new GraduationContext(2, 2, "t1")));
    }

    @Test
    void eitherAboveThreshold_passes() {
        assertEquals(0.7, scorer.score(memory(0.7),
                                       new GraduationContext(3, 0, "t1")));
        assertEquals(0.7, scorer.score(memory(0.7),
                                       new GraduationContext(0, 3, "t1")));
    }

    private Memory formativeMemory(Double confidence, double salience) {
        var attrs = Map.of(
                "event-type", "formative",
                "salience-multiplier", String.valueOf(salience),
                "catalogue-entry-id", "test",
                "situation-types", "formation");
        return new Memory("m1", Subject.of("agent", "alice"),
                          new MemoryDomain("experience"), "t1", null,
                          "childhood experience", attrs, Instant.now(),
                          confidence != null ? Confidence.unknown(confidence) : null,
                          null, null, null, null, Set.of());
    }

    @Test
    void formative_bypassesCorroboration() {
        double score = scorer.score(
                formativeMemory(0.9, 1.5),
                new GraduationContext(0, "t1"));
        assertEquals(1.0, score, 0.01, "min(1.0, 0.9 * 1.5) = 1.0");
    }

    @Test
    void formative_scoreClampedToOne() {
        double score = scorer.score(
                formativeMemory(0.8, 1.5),
                new GraduationContext(0, "t1"));
        assertEquals(1.0, score, 0.01, "min(1.0, 0.8 * 1.5) clamped to 1.0");
    }

    @Test
    void formative_lowConfidence_scalesWithSalience() {
        double score = scorer.score(
                formativeMemory(0.3, 1.5),
                new GraduationContext(0, "t1"));
        assertEquals(0.45, score, 0.01, "0.3 * 1.5 = 0.45");
    }

    @Test
    void formative_nullConfidence_usesDefault08() {
        double score = scorer.score(
                formativeMemory(null, 1.5),
                new GraduationContext(0, "t1"));
        assertEquals(1.0, score, 0.01, "default 0.8 * 1.5 = 1.2 clamped to 1.0");
    }

    @Test
    void formative_unitSalience_returnsConfidence() {
        double score = scorer.score(
                formativeMemory(0.7, 1.0),
                new GraduationContext(0, "t1"));
        assertEquals(0.7, score, 0.01, "0.7 * 1.0 = 0.7");
    }


}
