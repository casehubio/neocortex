package io.casehub.neocortex.memory.personality;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PersonalityWeightedRetrievalTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");
    private static final MemoryDomain RELATIONSHIP = new MemoryDomain("relationship");
    private static final MemoryDomain REFLECTION = new MemoryDomain("reflection");

    private Memory memory(String id, MemoryDomain domain, Instant createdAt, Double importance) {
        return new Memory(id, "a1", domain, "t1", null, "text", Map.of(), createdAt, importance);
    }

    @Test
    void domainWeightReordersSamAgeMemories() {
        var recent = NOW.minus(1, ChronoUnit.HOURS);
        var exp = memory("m1", EXPERIENCE, recent, 0.5);
        var rel = memory("m2", RELATIONSHIP, recent, 0.5);

        var weights = new PersonalityWeights(Map.of(RELATIONSHIP, 2.0));
        var result = PersonalityWeightedRetrieval.reweight(List.of(exp, rel), weights, NOW);

        assertEquals("m2", result.getFirst().memoryId());
        assertEquals("m1", result.get(1).memoryId());
    }

    @Test
    void recencyDecayApplies() {
        var recent = NOW.minus(1, ChronoUnit.HOURS);
        var old = NOW.minus(14, ChronoUnit.DAYS);
        var recentMem = memory("recent", EXPERIENCE, recent, 0.5);
        var oldMem = memory("old", EXPERIENCE, old, 0.5);

        var weights = new PersonalityWeights(Map.of());
        var result = PersonalityWeightedRetrieval.reweight(List.of(oldMem, recentMem), weights, NOW);

        assertEquals("recent", result.getFirst().memoryId());
    }

    @Test
    void nullImportanceTreatedAsOne() {
        var recent = NOW.minus(1, ChronoUnit.HOURS);
        var withImportance = memory("m1", EXPERIENCE, recent, 0.3);
        var withoutImportance = memory("m2", EXPERIENCE, recent, null);

        var weights = new PersonalityWeights(Map.of());
        var result = PersonalityWeightedRetrieval.reweight(List.of(withImportance, withoutImportance), weights, NOW);

        assertEquals("m2", result.getFirst().memoryId());
    }

    @Test
    void emptyListReturnsEmpty() {
        var weights = new PersonalityWeights(Map.of());
        var result = PersonalityWeightedRetrieval.reweight(List.of(), weights, NOW);
        assertTrue(result.isEmpty());
    }

    @Test
    void originalListUnchanged() {
        var recent = NOW.minus(1, ChronoUnit.HOURS);
        var old = NOW.minus(7, ChronoUnit.DAYS);
        var original = List.of(memory("old", EXPERIENCE, old, 0.5), memory("recent", EXPERIENCE, recent, 0.5));

        var weights = new PersonalityWeights(Map.of());
        PersonalityWeightedRetrieval.reweight(original, weights, NOW);

        assertEquals("old", original.getFirst().memoryId());
    }

    @Test
    void highWeightOvercomesRecencyDisadvantage() {
        var recent = NOW.minus(2, ChronoUnit.HOURS);
        var slightlyOlder = NOW.minus(6, ChronoUnit.HOURS);
        var recentExp = memory("exp", EXPERIENCE, recent, 0.5);
        var olderRel = memory("rel", RELATIONSHIP, slightlyOlder, 0.5);

        var weights = new PersonalityWeights(Map.of(RELATIONSHIP, 3.0));
        var result = PersonalityWeightedRetrieval.reweight(List.of(recentExp, olderRel), weights, NOW);

        assertEquals("rel", result.getFirst().memoryId());
    }

    @Test
    void multipleDomainsWeightedCorrectly() {
        var time = NOW.minus(1, ChronoUnit.HOURS);
        var exp = memory("exp", EXPERIENCE, time, 0.5);
        var rel = memory("rel", RELATIONSHIP, time, 0.5);
        var ref = memory("ref", REFLECTION, time, 0.5);

        var weights = new PersonalityWeights(Map.of(
            RELATIONSHIP, 2.0,
            REFLECTION, 0.5
        ));
        var result = PersonalityWeightedRetrieval.reweight(List.of(exp, rel, ref), weights, NOW);

        assertEquals("rel", result.get(0).memoryId());
        assertEquals("exp", result.get(1).memoryId());
        assertEquals("ref", result.get(2).memoryId());
    }
}
