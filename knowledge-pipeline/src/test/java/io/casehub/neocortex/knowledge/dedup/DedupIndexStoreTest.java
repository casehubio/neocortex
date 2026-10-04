package io.casehub.neocortex.knowledge.dedup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DedupIndexStoreTest {

    private DedupIndexStore store;

    @BeforeEach
    void setUp() {
        store = new DedupIndexStore(":memory:");
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void lookupReturnsEmptyForUnknownEntity() {
        assertThat(store.lookup("google", "xyz")).isEmpty();
    }

    @Test
    void upsertAndLookupRoundTrips() {
        store.upsert("google", "ChIJ123", "cache-1");
        var entry = store.lookup("google", "ChIJ123");
        assertThat(entry).isPresent();
        assertThat(entry.get().cacheEntityId()).isEqualTo("cache-1");
        assertThat(entry.get().mindMapNodeId()).isNull();
    }

    @Test
    void upsertUpdatesLastSeen() throws InterruptedException {
        store.upsert("google", "ChIJ123", "cache-1");
        var first = store.lookup("google", "ChIJ123").get().lastSeen();
        Thread.sleep(10);
        store.upsert("google", "ChIJ123", "cache-1");
        var second = store.lookup("google", "ChIJ123").get().lastSeen();
        assertThat(second).isAfterOrEqualTo(first);
    }

    @Test
    void setMindMapNodeIdUpdatesEntry() {
        store.upsert("google", "ChIJ123", "cache-1");
        store.setMindMapNodeId("google", "ChIJ123", "node-42");
        var entry = store.lookup("google", "ChIJ123").get();
        assertThat(entry.mindMapNodeId()).isEqualTo("node-42");
    }

    @Test
    void differentSourcesSameExternalIdAreSeparate() {
        store.upsert("google", "123", "cache-g");
        store.upsert("tripadvisor", "123", "cache-t");
        assertThat(store.lookup("google", "123").get().cacheEntityId()).isEqualTo("cache-g");
        assertThat(store.lookup("tripadvisor", "123").get().cacheEntityId()).isEqualTo("cache-t");
    }
}
