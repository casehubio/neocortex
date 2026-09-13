package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.mood.AffectEvents;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EventTriggeredAnalyzerTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void emptyExperienceReturnsEmptyImpact() {
        var result = EventTriggeredAnalyzer.analyze(
                List.of(), makeAffectSeries(10), Duration.ofHours(24));
        assertEquals(0, result.eventCount());
        assertEquals(0, result.totalEvents());
        assertTrue(result.byType().isEmpty());
    }

    @Test
    void emptyAffectReturnsZeroEventCount() {
        var result = EventTriggeredAnalyzer.analyze(
                List.of(makeExperience(BASE, "observation")),
                List.of(), Duration.ofHours(24));
        assertEquals(0, result.eventCount());
        assertEquals(1, result.totalEvents());
    }

    @Test
    void eventsAtAffectInflectionProduceNonZeroDelta() {
        List<Memory> affect = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            double p = i < 24 ? -0.5 : 0.5;
            affect.add(makeAffect(BASE.plus(Duration.ofHours(i)), p, 0.0, 0.0));
        }
        Memory event = makeExperience(BASE.plus(Duration.ofHours(24)), "outcome");

        var result = EventTriggeredAnalyzer.analyze(
                List.of(event), affect, Duration.ofHours(24));

        assertEquals(1, result.eventCount());
        double pleasureDelta = result.meanDelta().get(PadDimension.PLEASURE);
        assertTrue(Math.abs(pleasureDelta) > 0.5,
                   "pleasure delta should be large at inflection, got " + pleasureDelta);
    }

    @Test
    void eventsDuringFlatAffectProduceNearZeroDelta() {
        List<Memory> affect = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            affect.add(makeAffect(BASE.plus(Duration.ofHours(i)), 0.5, 0.0, 0.0));
        }
        Memory event = makeExperience(BASE.plus(Duration.ofHours(24)), "observation");

        var result = EventTriggeredAnalyzer.analyze(
                List.of(event), affect, Duration.ofHours(24));

        assertEquals(1, result.eventCount());
        double pleasureDelta = result.meanDelta().get(PadDimension.PLEASURE);
        assertTrue(Math.abs(pleasureDelta) < 0.01,
                   "pleasure delta should be near zero for flat affect, got " + pleasureDelta);
    }

    @Test
    void perTypeBreakdownPartitionsCorrectly() {
        List<Memory> affect = new ArrayList<>();
        for (int i = 0; i < 72; i++) {
            double p = i * 0.01;
            affect.add(makeAffect(BASE.plus(Duration.ofHours(i)), p, 0.0, 0.0));
        }
        List<Memory> events = List.of(
                makeExperience(BASE.plus(Duration.ofHours(12)), "observation"),
                makeExperience(BASE.plus(Duration.ofHours(36)), "outcome"),
                makeExperience(BASE.plus(Duration.ofHours(60)), "outcome")
        );

        var result = EventTriggeredAnalyzer.analyze(events, affect, Duration.ofHours(12));

        assertTrue(result.byType().containsKey("observation"));
        assertTrue(result.byType().containsKey("outcome"));
        assertEquals(1, result.byType().get("observation").eventCount());
        assertEquals(2, result.byType().get("outcome").eventCount());
    }

    @Test
    void sparseAffectSkipsEventsWithInsufficientData() {
        List<Memory> affect = List.of(
                makeAffect(BASE, 0.5, 0.0, 0.0));
        List<Memory> events = List.of(
                makeExperience(BASE.plus(Duration.ofDays(10)), "observation"));

        var result = EventTriggeredAnalyzer.analyze(events, affect, Duration.ofHours(24));

        assertEquals(0, result.eventCount());
        assertEquals(1, result.totalEvents());
    }

    @Test
    void bootstrapCIBracketsMeanDelta() {
        List<Memory> affect = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            double p = i < 50 ? 0.0 : 0.5;
            affect.add(makeAffect(BASE.plus(Duration.ofHours(i)), p, 0.0, 0.0));
        }
        List<Memory> events = new ArrayList<>();
        for (int i = 45; i < 55; i++) {
            events.add(makeExperience(BASE.plus(Duration.ofHours(i)), "observation"));
        }

        var result = EventTriggeredAnalyzer.analyze(events, affect, Duration.ofHours(6));

        if (result.eventCount() > 1 && result.confidenceInterval().containsKey(PadDimension.PLEASURE)) {
            var ci = result.confidenceInterval().get(PadDimension.PLEASURE);
            double mean = result.meanDelta().get(PadDimension.PLEASURE);
            assertTrue(ci.lower() <= mean && mean <= ci.upper(),
                       "CI [" + ci.lower() + ", " + ci.upper() + "] should bracket mean " + mean);
        }
    }

    @Test
    void perTypeCIPresent() {
        List<Memory> affect = new ArrayList<>();
        for (int i = 0; i < 72; i++) {
            double p = i * 0.01;
            affect.add(makeAffect(BASE.plus(Duration.ofHours(i)), p, 0.0, 0.0));
        }
        List<Memory> events = List.of(
                makeExperience(BASE.plus(Duration.ofHours(12)), "outcome"),
                makeExperience(BASE.plus(Duration.ofHours(36)), "outcome"),
                makeExperience(BASE.plus(Duration.ofHours(60)), "outcome")
        );

        var result = EventTriggeredAnalyzer.analyze(events, affect, Duration.ofHours(12));
        var outcomeImpact = result.byType().get("outcome");
        assertNotNull(outcomeImpact);
        assertFalse(outcomeImpact.confidenceInterval().isEmpty(),
                    "per-type CI should be present when eventCount >= 2");
    }

    private List<Memory> makeAffectSeries(int count) {
        List<Memory> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(makeAffect(BASE.plus(Duration.ofHours(i)), i * 0.05, 0.0, 0.0));
        }
        return result;
    }

    private Memory makeAffect(Instant time, double p, double a, double d) {
        return new Memory(UUID.randomUUID().toString(),
                Subject.of("unknown", "entity-1"), AffectEvents.DOMAIN, "tenant",
                null, "affect", Map.of(), time,
                null, p, a, d, null, null);
    }

    private Memory makeExperience(Instant time, String eventType) {
        return new Memory(UUID.randomUUID().toString(),
                Subject.of("agent", "agent-1"), new MemoryDomain("experience"), "tenant",
                null, "event", Map.of(ExperienceAttributeKeys.EVENT_TYPE, eventType), time,
                null, null, null, null, null, null);
    }
}
