package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.reflection.*;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionServiceTest {

    private StubStore store;
    private StubSynthesizer synthesizer;
    private StubEvent<ReflectionRecorded> eventSink;
    private ReflectionService service;

    @BeforeEach
    void setUp() {
        store = new StubStore();
        synthesizer = new StubSynthesizer();
        eventSink = new StubEvent<>();
        service = new ReflectionService(store, synthesizer, eventSink);
    }

    @Test
    void reflectQueriesSynthesizesStoresAndReturnsIds() {
        store.queryResults = List.of(
            new Memory("m1", "a1", new MemoryDomain("experience"), "t1", null, "saw something", Map.of(), Instant.now(), null),
            new Memory("m2", "a1", new MemoryDomain("experience"), "t1", null, "did something", Map.of(), Instant.now(), null)
        );
        synthesizer.results = List.of(
            new ReflectionEvent("a1", "t1", null, null, "pattern observed", 1, List.of("m1", "m2"), null, Map.of())
        );

        List<String> ids = service.reflect("a1", "t1", null, 100);

        assertEquals(1, ids.size());
        assertEquals("mem-0", ids.getFirst());
        assertEquals(1, store.stored.size());
        assertEquals(ReflectionEvents.DOMAIN, store.stored.getFirst().domain());
    }

    @Test
    void reflectFiresCdiEventPerReflection() {
        store.queryResults = List.of(
            new Memory("m1", "a1", new MemoryDomain("experience"), "t1", null, "text", Map.of(), Instant.now(), null)
        );
        synthesizer.results = List.of(
            new ReflectionEvent("a1", "t1", null, null, "insight 1", 1, List.of("m1"), null, Map.of()),
            new ReflectionEvent("a1", "t1", null, null, "insight 2", 1, List.of("m1"), null, Map.of())
        );

        service.reflect("a1", "t1", null, 100);

        assertEquals(2, eventSink.fired.size());
        assertEquals("insight 1", eventSink.fired.get(0).event().insight());
        assertEquals("insight 2", eventSink.fired.get(1).event().insight());
    }

    @Test
    void reflectReturnsEmptyWhenNoExperiences() {
        store.queryResults = List.of();
        List<String> ids = service.reflect("a1", "t1", null, 100);
        assertTrue(ids.isEmpty());
        assertFalse(synthesizer.wasCalled);
    }

    @Test
    void reflectReturnsEmptyWhenSynthesizerReturnsEmpty() {
        store.queryResults = List.of(
            new Memory("m1", "a1", new MemoryDomain("experience"), "t1", null, "text", Map.of(), Instant.now(), null)
        );
        synthesizer.results = List.of();

        List<String> ids = service.reflect("a1", "t1", null, 100);

        assertTrue(ids.isEmpty());
        assertTrue(store.stored.isEmpty());
        assertTrue(eventSink.fired.isEmpty());
    }

    @Test
    void reflectPassesSinceToQuery() {
        var since = Instant.parse("2026-08-01T00:00:00Z");
        store.queryResults = List.of();

        service.reflect("a1", "t1", since, 100);

        assertNotNull(store.lastQuery);
        assertEquals(since, store.lastQuery.since());
    }

    @Test
    void reflectPropagatesSecurityException() {
        store.throwOnStore = new SecurityException("forbidden");
        store.queryResults = List.of(
            new Memory("m1", "a1", new MemoryDomain("experience"), "t1", null, "text", Map.of(), Instant.now(), null)
        );
        synthesizer.results = List.of(
            new ReflectionEvent("a1", "t1", null, null, "insight", 1, List.of("m1"), null, Map.of())
        );

        assertThrows(SecurityException.class, () -> service.reflect("a1", "t1", null, 100));
    }

    @Test
    void noOpSynthesizerReturnsEmptyList() {
        var noOp = new NoOpReflectionSynthesizer();
        var result = noOp.synthesize("a1", "t1", List.of(), 1);
        assertTrue(result.isEmpty());
    }

    // --- Stubs ---

    static class StubStore implements CaseMemoryStore {
        final List<MemoryInput> stored = new ArrayList<>();
        List<Memory> queryResults = List.of();
        MemoryQuery lastQuery;
        int counter = 0;
        RuntimeException throwOnStore;

        @Override public String store(MemoryInput input) {
            if (throwOnStore != null) throw throwOnStore;
            stored.add(input);
            return "mem-" + counter++;
        }
        @Override public List<Memory> query(MemoryQuery query) {
            lastQuery = query;
            return queryResults;
        }
        @Override public int erase(io.casehub.neocortex.memory.EraseRequest r) { return 0; }
    }

    static class StubSynthesizer implements ReflectionSynthesizer {
        List<ReflectionEvent> results = List.of();
        boolean wasCalled = false;

        @Override
        public List<ReflectionEvent> synthesize(String agentId, String tenantId,
                List<Memory> sources, int targetLevel) {
            wasCalled = true;
            return results;
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
