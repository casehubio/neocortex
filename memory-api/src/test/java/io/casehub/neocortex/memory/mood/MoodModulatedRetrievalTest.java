package io.casehub.neocortex.memory.mood;

import io.casehub.neocortex.cognitive.Confidence;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import io.casehub.neocortex.memory.personality.PersonalityWeightedRetrieval;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MoodModulatedRetrievalTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");
    private static final MemoryDomain EXP = new MemoryDomain("experience");
    private static final PersonalityWeights EQUAL_WEIGHTS = new PersonalityWeights(Map.of());

    private Memory memory(String id, Instant createdAt, Map<String, String> attrs) {
        return new Memory(id, "a1", EXP, "t1", null, "text", attrs, createdAt, Confidence.unknown(0.5));
    }

    private MoodState mood(double p, double a, double d) {
        return new MoodState("a1", "t1", p, a, d, "test", null, Map.of());
    }

    @Test
    void moodAlignedMemoryBoosted() {
        var recent = NOW.minus(1, ChronoUnit.HOURS);
        var happy = memory("happy", recent, Map.of(
            MoodAttributeKeys.PLEASURE, "0.8",
            MoodAttributeKeys.AROUSAL, "0.3",
            MoodAttributeKeys.DOMINANCE, "0.2"));
        var sad = memory("sad", recent, Map.of(
            MoodAttributeKeys.PLEASURE, "-0.8",
            MoodAttributeKeys.AROUSAL, "-0.3",
            MoodAttributeKeys.DOMINANCE, "-0.2"));

        var currentMood = mood(0.8, 0.3, 0.2);
        var result = MoodModulatedRetrieval.reweight(
            List.of(sad, happy), EQUAL_WEIGHTS, currentMood, 1.0, NOW);

        assertEquals("happy", result.getFirst().memoryId());
    }

    @Test
    void unannotatedMemoriesUnaffected() {
        var recent = NOW.minus(1, ChronoUnit.HOURS);
        var annotated = memory("annotated", recent, Map.of(
            MoodAttributeKeys.PLEASURE, "1.0",
            MoodAttributeKeys.AROUSAL, "1.0",
            MoodAttributeKeys.DOMINANCE, "1.0"));
        var plain = memory("plain", recent, Map.of());

        var currentMood = mood(-1.0, -1.0, -1.0);
        var result = MoodModulatedRetrieval.reweight(
            List.of(annotated, plain), EQUAL_WEIGHTS, currentMood, 1.0, NOW);

        assertEquals("plain", result.getFirst().memoryId());
    }

    @Test
    void zeroInfluenceMatchesPersonalityWeightedRetrieval() {
        var t1 = NOW.minus(1, ChronoUnit.HOURS);
        var t2 = NOW.minus(2, ChronoUnit.HOURS);
        var m1 = memory("m1", t1, Map.of(MoodAttributeKeys.PLEASURE, "0.9",
            MoodAttributeKeys.AROUSAL, "0.0", MoodAttributeKeys.DOMINANCE, "0.0"));
        var m2 = memory("m2", t2, Map.of(MoodAttributeKeys.PLEASURE, "-0.9",
            MoodAttributeKeys.AROUSAL, "0.0", MoodAttributeKeys.DOMINANCE, "0.0"));

        var currentMood = mood(0.9, 0.0, 0.0);
        var moodResult = MoodModulatedRetrieval.reweight(
            List.of(m1, m2), EQUAL_WEIGHTS, currentMood, 0.0, NOW);
        var personalityResult = PersonalityWeightedRetrieval.reweight(
            List.of(m1, m2), EQUAL_WEIGHTS, NOW);

        assertEquals(personalityResult.getFirst().memoryId(),
            moodResult.getFirst().memoryId());
    }

    @Test
    void emptyListReturnsEmpty() {
        var result = MoodModulatedRetrieval.reweight(
            List.of(), EQUAL_WEIGHTS, mood(0.0, 0.0, 0.0), 1.0, NOW);
        assertTrue(result.isEmpty());
    }

    @Test
    void partialPadDefaultsMissingToZero() {
        var recent = NOW.minus(1, ChronoUnit.HOURS);
        var partial = memory("partial", recent, Map.of(
            MoodAttributeKeys.PLEASURE, "0.8"));
        var full = memory("full", recent, Map.of(
            MoodAttributeKeys.PLEASURE, "0.8",
            MoodAttributeKeys.AROUSAL, "0.8",
            MoodAttributeKeys.DOMINANCE, "0.8"));

        var currentMood = mood(0.8, 0.8, 0.8);
        var result = MoodModulatedRetrieval.reweight(
            List.of(partial, full), EQUAL_WEIGHTS, currentMood, 1.0, NOW);

        assertEquals("full", result.getFirst().memoryId());
    }

    @Test
    void rejectsMoodInfluenceOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
            MoodModulatedRetrieval.reweight(
                List.of(), EQUAL_WEIGHTS, mood(0.0, 0.0, 0.0), 1.1, NOW));
        assertThrows(IllegalArgumentException.class, () ->
            MoodModulatedRetrieval.reweight(
                List.of(), EQUAL_WEIGHTS, mood(0.0, 0.0, 0.0), -0.1, NOW));
    }

    @Test
    void originalListUnchanged() {
        var recent = NOW.minus(1, ChronoUnit.HOURS);
        var old = NOW.minus(7, ChronoUnit.DAYS);
        var original = List.of(
            memory("old", old, Map.of()),
            memory("recent", recent, Map.of()));

        MoodModulatedRetrieval.reweight(original, EQUAL_WEIGHTS, mood(0.0, 0.0, 0.0), 0.5, NOW);
        assertEquals("old", original.getFirst().memoryId());
    }
}
