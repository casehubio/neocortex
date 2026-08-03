package io.casehub.neocortex.memory.experience.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.StoreAllResult;
import io.casehub.neocortex.memory.StoreFailure;
import io.casehub.neocortex.memory.experience.*;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceStreamTest {

    private StubCaseMemoryStore store;
    private StubEvent<ExperienceRecorded> eventSink;
    private ExperienceStream stream;

    @BeforeEach
    void setUp() {
        store = new StubCaseMemoryStore();
        eventSink = new StubEvent<>();
        stream = new ExperienceStream(store, eventSink);
    }

    @Test
    void recordStoresAndReturnsMemoryId() {
        var obs = new Observation("a1", "t1", null, null, "saw it", null, Map.of(), "subj");
        String id = stream.record(obs);
        assertEquals("mem-0", id);
        assertEquals(1, store.stored.size());
        assertEquals(ExperienceEvents.DOMAIN, store.stored.getFirst().domain());
    }

    @Test
    void recordFiresCdiEvent() {
        var obs = new Observation("a1", "t1", null, null, "saw it", null, Map.of(), "subj");
        stream.record(obs);
        assertEquals(1, eventSink.fired.size());
        assertEquals("mem-0", eventSink.fired.getFirst().memoryId());
        assertSame(obs, eventSink.fired.getFirst().event());
    }

    @Test
    void recordPropagatesSecurityException() {
        store.throwOnStore = new SecurityException("forbidden");
        var obs = new Observation("a1", "t1", null, null, "saw it", null, Map.of(), "subj");
        assertThrows(SecurityException.class, () -> stream.record(obs));
        assertTrue(eventSink.fired.isEmpty());
    }

    @Test
    void recordAllReturnsResult() {
        var obs = new Observation("a1", "t1", null, null, "saw it", null, Map.of(), "subj");
        var act = new Action("a1", "t1", null, null, "did it", null, Map.of(), null);
        ExperienceStoreResult result = stream.recordAll(List.of(obs, act));
        assertTrue(result.allSucceeded());
        assertEquals(2, result.stored().size());
    }

    @Test
    void recordAllFiresEventPerStoredItem() {
        var obs = new Observation("a1", "t1", null, null, "saw it", null, Map.of(), "subj");
        var act = new Action("a1", "t1", null, null, "did it", null, Map.of(), null);
        stream.recordAll(List.of(obs, act));
        assertEquals(2, eventSink.fired.size());
    }

    @Test
    void recordAllHandlesPartialFailure() {
        store.failAtIndex = 1;
        var obs = new Observation("a1", "t1", null, null, "saw it", null, Map.of(), "subj");
        var act = new Action("a1", "t1", null, null, "did it", null, Map.of(), null);
        var out = new Outcome("a1", "t1", null, null, "done", null, Map.of(), "ok", null);
        ExperienceStoreResult result = stream.recordAll(List.of(obs, act, out));

        assertFalse(result.allSucceeded());
        assertEquals(2, result.stored().size());
        assertEquals(1, result.failures().size());
        assertEquals(1, result.failures().getFirst().inputIndex());
        assertSame(act, result.failures().getFirst().event());
        assertEquals(2, eventSink.fired.size());
    }

    @Test
    void recordAllWithEmptyListReturnsEmptyResult() {
        ExperienceStoreResult result = stream.recordAll(List.of());
        assertTrue(result.allSucceeded());
        assertTrue(result.stored().isEmpty());
    }

    // --- Stubs ---

    static class StubCaseMemoryStore implements CaseMemoryStore {
        final List<MemoryInput> stored = new ArrayList<>();
        int counter = 0;
        RuntimeException throwOnStore;
        int failAtIndex = -1;

        @Override
        public String store(MemoryInput input) {
            if (throwOnStore != null) throw throwOnStore;
            stored.add(input);
            return "mem-" + counter++;
        }

        @Override
        public List<io.casehub.neocortex.memory.Memory> query(io.casehub.neocortex.memory.MemoryQuery query) {
            return List.of();
        }

        @Override
        public int erase(io.casehub.neocortex.memory.EraseRequest request) {
            return 0;
        }

        @Override
        public StoreAllResult storeAll(List<MemoryInput> inputs) {
            var ids = new ArrayList<String>();
            var failures = new ArrayList<StoreFailure>();
            for (int i = 0; i < inputs.size(); i++) {
                if (i == failAtIndex) {
                    failures.add(new StoreFailure(i, inputs.get(i), new RuntimeException("fail")));
                } else {
                    stored.add(inputs.get(i));
                    ids.add("mem-" + counter++);
                }
            }
            return new StoreAllResult(ids, failures);
        }
    }

    static class StubEvent<T> implements Event<T> {
        final List<T> fired = new ArrayList<>();

        @Override public void fire(T event) { fired.add(event); }
        @Override public <U extends T> CompletionStage<U> fireAsync(U event) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> CompletionStage<U> fireAsync(U event, jakarta.enterprise.event.NotificationOptions options) { throw new UnsupportedOperationException(); }
        @Override public Event<T> select(java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> Event<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    }
}
