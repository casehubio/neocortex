package io.casehub.neocortex.memory.engagement.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.StoreAllResult;
import io.casehub.neocortex.memory.StoreFailure;
import io.casehub.neocortex.memory.engagement.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EngagementRecorderCoreTest {

    private StubCaseMemoryStore store;
    private List<EngagementRecorded> recorded;
    private EngagementRecorderCore recorder;

    @BeforeEach
    void setUp() {
        store = new StubCaseMemoryStore();
        recorded = new ArrayList<>();
        recorder = new EngagementRecorderCore(store, recorded::add);
    }

    private EngagementEvent engagement() {
        return new EngagementEvent("a1", "b1", "t1", null, "turn-1",
                                   null, "user responded", null, Map.of(), true, null, null, null, null, null);
    }

    @Test void recordStoresAndReturnsMemoryId() {
        String id = recorder.record(engagement());
        assertEquals("mem-0", id);
        assertEquals(1, store.stored.size());
        assertEquals(EngagementEvents.DOMAIN, store.stored.getFirst().domain());
    }

    @Test void recordFiresCallback() {
        var event = engagement();
        recorder.record(event);
        assertEquals(1, recorded.size());
        assertEquals("mem-0", recorded.getFirst().memoryId());
        assertSame(event, recorded.getFirst().event());
    }

    @Test void recordAllReturnsResult() {
        EngagementStoreResult result = recorder.recordAll(List.of(engagement(), engagement()));
        assertTrue(result.allSucceeded());
        assertEquals(2, result.stored().size());
    }

    @Test void recordAllFiresCallbackPerStoredItem() {
        recorder.recordAll(List.of(engagement(), engagement()));
        assertEquals(2, recorded.size());
    }

    @Test void recordAllHandlesPartialFailure() {
        store.failAtIndex = 1;
        var e1 = engagement(); var e2 = engagement(); var e3 = engagement();
        EngagementStoreResult result = recorder.recordAll(List.of(e1, e2, e3));
        assertFalse(result.allSucceeded());
        assertEquals(2, result.stored().size());
        assertEquals(1, result.failures().size());
        assertEquals(1, result.failures().getFirst().inputIndex());
        assertSame(e2, result.failures().getFirst().event());
        assertEquals(2, recorded.size());
    }

    @Test void recordPropagatesSecurityException() {
        store.throwOnStore = new SecurityException("forbidden");
        assertThrows(SecurityException.class, () -> recorder.record(engagement()));
        assertTrue(recorded.isEmpty());
    }

    @Test void recordAllWithEmptyListReturnsEmptyResult() {
        EngagementStoreResult result = recorder.recordAll(List.of());
        assertTrue(result.allSucceeded());
        assertTrue(result.stored().isEmpty());
    }

    static class StubCaseMemoryStore implements CaseMemoryStore {
        final List<MemoryInput> stored = new ArrayList<>();
        int counter = 0;
        RuntimeException throwOnStore;
        int failAtIndex = -1;

        @Override public String store(MemoryInput input) {
            if (throwOnStore != null) throw throwOnStore;
            stored.add(input);
            return "mem-" + counter++;
        }
        @Override public List<io.casehub.neocortex.memory.Memory> query(io.casehub.neocortex.memory.MemoryQuery query) { return List.of(); }
        @Override public int erase(io.casehub.neocortex.memory.EraseRequest request) { return 0; }
        @Override public StoreAllResult storeAll(List<MemoryInput> inputs) {
            var ids = new ArrayList<String>();
            var failures = new ArrayList<StoreFailure>();
            for (int i = 0; i < inputs.size(); i++) {
                if (i == failAtIndex) {
                    failures.add(new StoreFailure(i, inputs.get(i), new RuntimeException("fail")));
                } else { stored.add(inputs.get(i)); ids.add("mem-" + counter++); }
            }
            return new StoreAllResult(ids, failures);
        }
    }
}
