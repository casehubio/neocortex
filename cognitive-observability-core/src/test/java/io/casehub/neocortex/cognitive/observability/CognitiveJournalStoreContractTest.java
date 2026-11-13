package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.EventRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveJournalStoreContractTest {

    private CognitiveJournalStore store;
    private static final String T = "tenant1";
    private static final String A = "agent1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        store = new InMemoryCognitiveJournalStore();
    }

    @Test
    void persistAndFindByAgent() {
        var entry = new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "test");
        store.persist(entry);
        var found = store.findByAgent(T, A);
        assertEquals(1, found.size());
        assertEquals(entry, found.getFirst());
    }

    @Test
    void findByEventRef() {
        var ref = EventRef.tick(5);
        store.persist(new CognitiveEntry.MoodShifted(T, A, ref, NOW, null, null, "match"));
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(6), NOW, null, null, "no-match"));
        var found = store.findByEventRef(T, A, ref);
        assertEquals(1, found.size());
    }

    @Test
    void findByTickRange() {
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "t1"));
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(5), NOW, null, null, "t5"));
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(10), NOW, null, null, "t10"));
        var found = store.findByTickRange(T, A, 1, 5);
        assertEquals(2, found.size());
    }

    @Test
    void findByType() {
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "m"));
        store.persist(new CognitiveEntry.DriveActivated(T, A, EventRef.tick(1), NOW, List.of(), Map.of()));
        var moods = store.findByType(T, A, CognitiveEntry.MoodShifted.class);
        assertEquals(1, moods.size());
    }

    @Test
    void persistAllBatch() {
        var entries = List.<CognitiveEntry>of(
                new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "a"),
                new CognitiveEntry.MoodShifted(T, A, EventRef.tick(2), NOW, null, null, "b")
        );
        store.persistAll(entries);
        assertEquals(2, store.findByAgent(T, A).size());
    }

    @Test
    void tenantIsolation() {
        store.persist(new CognitiveEntry.MoodShifted("t1", A, EventRef.tick(1), NOW, null, null, "a"));
        store.persist(new CognitiveEntry.MoodShifted("t2", A, EventRef.tick(1), NOW, null, null, "b"));
        assertEquals(1, store.findByAgent("t1", A).size());
        assertEquals(1, store.findByAgent("t2", A).size());
    }

    @Test
    void findByTimeWindow() {
        var t1 = Instant.parse("2026-01-01T00:00:00Z");
        var t2 = Instant.parse("2026-01-01T01:00:00Z");
        var t3 = Instant.parse("2026-01-01T02:00:00Z");
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), t1, null, null, "early"));
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(2), t2, null, null, "mid"));
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(3), t3, null, null, "late"));
        var found = store.findByTimeWindow(T, A, t1, t2);
        assertEquals(2, found.size());
    }

    @Test
    void findByTickRangeIgnoresNonTickEvents() {
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "tick"));
        store.persist(new CognitiveEntry.MoodShifted(T, A, EventRef.init(), NOW, null, null, "init"));
        var found = store.findByTickRange(T, A, 0, 10);
        assertEquals(1, found.size());
    }
}
