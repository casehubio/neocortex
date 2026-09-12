package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.reflection.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionOrchestratorCoreTest {

    private StubStore store;
    private StubSynthesizer synthesizer;
    private List<ReflectionRecorded> recorded;
    private ReflectionOrchestratorCore orchestrator;

    @BeforeEach
    void setUp() {
        store = new StubStore();
        synthesizer = new StubSynthesizer();
        recorded = new ArrayList<>();
        orchestrator = new ReflectionOrchestratorCore(store, synthesizer, recorded::add);
    }

    @Test void reflectQueriesSynthesizesStoresAndReturnsIds() {
        store.queryResults = List.of(
            new Memory("m1", "a1", new MemoryDomain("experience"), "t1", null, "saw something", Map.of(), Instant.now(), null, null, null, null),
            new Memory("m2", "a1", new MemoryDomain("experience"), "t1", null, "did something", Map.of(), Instant.now(), null, null, null, null)
        );
        synthesizer.results = List.of(
            new ReflectionEvent("a1", "t1", null, null, "pattern observed", 1, List.of("m1", "m2"), null, Map.of())
        );
        List<String> ids = orchestrator.reflect("a1", "t1", null, 100);
        assertEquals(1, ids.size());
        assertEquals("mem-0", ids.getFirst());
        assertEquals(1, store.stored.size());
        assertEquals(ReflectionEvents.DOMAIN, store.stored.getFirst().domain());
    }

    @Test void reflectFiresCallbackPerReflection() {
        store.queryResults = List.of(
            new Memory("m1", "a1", new MemoryDomain("experience"), "t1", null, "text", Map.of(), Instant.now(), null, null, null, null)
        );
        synthesizer.results = List.of(
            new ReflectionEvent("a1", "t1", null, null, "insight 1", 1, List.of("m1"), null, Map.of()),
            new ReflectionEvent("a1", "t1", null, null, "insight 2", 1, List.of("m1"), null, Map.of())
        );
        orchestrator.reflect("a1", "t1", null, 100);
        assertEquals(2, recorded.size());
        assertEquals("insight 1", recorded.get(0).event().insight());
        assertEquals("insight 2", recorded.get(1).event().insight());
    }

    @Test void reflectReturnsEmptyWhenNoExperiences() {
        store.queryResults = List.of();
        List<String> ids = orchestrator.reflect("a1", "t1", null, 100);
        assertTrue(ids.isEmpty());
        assertFalse(synthesizer.wasCalled);
    }

    @Test void reflectReturnsEmptyWhenSynthesizerReturnsEmpty() {
        store.queryResults = List.of(
            new Memory("m1", "a1", new MemoryDomain("experience"), "t1", null, "text", Map.of(), Instant.now(), null, null, null, null)
        );
        synthesizer.results = List.of();
        List<String> ids = orchestrator.reflect("a1", "t1", null, 100);
        assertTrue(ids.isEmpty());
        assertTrue(store.stored.isEmpty());
        assertTrue(recorded.isEmpty());
    }

    @Test void reflectPassesSinceToQuery() {
        var since = Instant.parse("2026-08-01T00:00:00Z");
        store.queryResults = List.of();
        orchestrator.reflect("a1", "t1", since, 100);
        assertNotNull(store.lastQuery);
        assertEquals(since, store.lastQuery.since());
    }

    @Test void reflectPropagatesSecurityException() {
        store.throwOnStore = new SecurityException("forbidden");
        store.queryResults = List.of(
            new Memory("m1", "a1", new MemoryDomain("experience"), "t1", null, "text", Map.of(), Instant.now(), null, null, null, null)
        );
        synthesizer.results = List.of(
            new ReflectionEvent("a1", "t1", null, null, "insight", 1, List.of("m1"), null, Map.of())
        );
        assertThrows(SecurityException.class, () -> orchestrator.reflect("a1", "t1", null, 100));
    }

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
        @Override public List<Memory> query(MemoryQuery query) { lastQuery = query; return queryResults; }
        @Override public int erase(io.casehub.neocortex.memory.EraseRequest r) { return 0; }
    }

    static class StubSynthesizer implements ReflectionSynthesizer {
        List<ReflectionEvent> results = List.of();
        boolean wasCalled = false;

        @Override public List<ReflectionEvent> synthesize(String agentId, String tenantId,
                List<Memory> sources, int targetLevel) {
            wasCalled = true;
            return results;
        }
    }
}
