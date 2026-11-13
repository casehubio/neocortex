package io.casehub.neocortex.cognition.journal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveTraceTest {

    private static final String T = "tenant1";
    private static final String A = "agent1";
    private static final EventRef REF = EventRef.tick(1);
    private static final Instant NOW = Instant.now();

    @Test
    void accumulatesEntries() {
        var trace = new CognitiveTrace();
        trace.record(new CognitiveEntry.MoodShifted(T, A, REF, NOW, null, null, "pre-appraisal"));
        trace.record(new CognitiveEntry.DriveActivated(T, A, REF, NOW, List.of("aggression"), Map.of()));
        assertEquals(2, trace.size());
        assertFalse(trace.isEmpty());
    }

    @Test
    void entriesForEvent() {
        var trace = new CognitiveTrace();
        var tick1 = EventRef.tick(1);
        var tick2 = EventRef.tick(2);
        trace.record(new CognitiveEntry.MoodShifted(T, A, tick1, NOW, null, null, "t1"));
        trace.record(new CognitiveEntry.MoodShifted(T, A, tick2, NOW, null, null, "t2"));
        assertEquals(1, trace.entriesForEvent(tick1).size());
    }

    @Test
    void entriesOfType() {
        var trace = new CognitiveTrace();
        trace.record(new CognitiveEntry.MoodShifted(T, A, REF, NOW, null, null, "t"));
        trace.record(new CognitiveEntry.DriveActivated(T, A, REF, NOW, List.of(), Map.of()));
        var moods = trace.entriesOfType(CognitiveEntry.MoodShifted.class);
        assertEquals(1, moods.size());
        assertInstanceOf(CognitiveEntry.MoodShifted.class, moods.getFirst());
    }

    @Test
    void entriesForAgent() {
        var trace = new CognitiveTrace();
        trace.record(new CognitiveEntry.MoodShifted(T, "a1", REF, NOW, null, null, "t"));
        trace.record(new CognitiveEntry.MoodShifted(T, "a2", REF, NOW, null, null, "t"));
        assertEquals(1, trace.entriesForAgent("a1").size());
    }

    @Test
    void predicateFilter() {
        var trace = new CognitiveTrace(e -> e instanceof CognitiveEntry.MoodShifted);
        trace.record(new CognitiveEntry.MoodShifted(T, A, REF, NOW, null, null, "t"));
        trace.record(new CognitiveEntry.DriveActivated(T, A, REF, NOW, List.of(), Map.of()));
        assertEquals(1, trace.size());
    }

    @Test
    void clearResetsState() {
        var trace = new CognitiveTrace();
        trace.record(new CognitiveEntry.MoodShifted(T, A, REF, NOW, null, null, "t"));
        trace.clear();
        assertTrue(trace.isEmpty());
        assertEquals(0, trace.size());
    }

    @Test
    void noopRecordsNothing() {
        CognitiveJournal.NOOP.record(new CognitiveEntry.MoodShifted(T, A, REF, NOW, null, null, "t"));
    }

    @Test
    void entriesReturnsUnmodifiableList() {
        var trace = new CognitiveTrace();
        trace.record(new CognitiveEntry.MoodShifted(T, A, REF, NOW, null, null, "t"));
        assertThrows(UnsupportedOperationException.class,
                () -> trace.entries().add(new CognitiveEntry.MoodShifted(T, A, REF, NOW, null, null, "x")));
    }
}
