package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveTrace;
import io.casehub.neocortex.cognition.journal.EventRef;
import io.casehub.neocortex.cognitive.observability.InMemoryCognitiveJournalStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScopedCognitiveJournalTest {

    private InMemoryCognitiveJournalStore store;
    private ScopedCognitiveJournal journal;
    private static final String T = "tenant1";
    private static final String A = "agent1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        store = new InMemoryCognitiveJournalStore();
        journal = new ScopedCognitiveJournal(store);
    }

    @Test
    void recordWithinScopeAccumulatesInTrace() {
        journal.beginScope(e -> true);
        journal.record(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "t"));
        CognitiveTrace trace = journal.endScope();
        assertEquals(1, trace.size());
    }

    @Test
    void recordAlsoPersistsToStore() {
        journal.beginScope(e -> true);
        journal.record(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "t"));
        journal.endScope();
        assertEquals(1, store.findByAgent(T, A).size());
    }

    @Test
    void recordWithoutScopeStillPersists() {
        journal.record(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "t"));
        assertEquals(1, store.findByAgent(T, A).size());
    }

    @Test
    void scopeFilterApplies() {
        journal.beginScope(e -> e instanceof CognitiveEntry.MoodShifted);
        journal.record(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "t"));
        journal.record(new CognitiveEntry.DriveActivated(T, A, EventRef.tick(1), NOW, List.of(), Map.of()));
        CognitiveTrace trace = journal.endScope();
        assertEquals(1, trace.size());
        assertEquals(2, store.findByAgent(T, A).size());
    }

    @Test
    void endScopeClearsThreadLocal() {
        journal.beginScope(e -> true);
        journal.record(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "t"));
        journal.endScope();
        journal.record(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(2), NOW, null, null, "no-trace"));
        assertEquals(2, store.findByAgent(T, A).size());
    }

    @Test
    void threadIsolation() throws Exception {
        journal.beginScope(e -> true);
        journal.record(new CognitiveEntry.MoodShifted(T, A, EventRef.tick(1), NOW, null, null, "main"));

        Thread other = new Thread(() -> {
            journal.beginScope(e -> true);
            journal.record(new CognitiveEntry.MoodShifted(T, "a2",
                    EventRef.consolidation(EventRef.tick(1)), NOW, null, null, "other"));
            CognitiveTrace otherTrace = journal.endScope();
            assertEquals(1, otherTrace.size());
        });
        other.start();
        other.join();

        CognitiveTrace mainTrace = journal.endScope();
        assertEquals(1, mainTrace.size());
        assertEquals("main", ((CognitiveEntry.MoodShifted) mainTrace.entries().getFirst()).trigger());
    }
}
