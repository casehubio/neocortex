package io.casehub.neocortex.knowledge.cache;

import com.zaxxer.hikari.HikariDataSource;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryCacheStoreTest {

    private HikariDataSource ds;
    private QueryCacheStore store;

    @BeforeEach
    void setUp() {
        ds = SqliteDataSourceFactory.create(":memory:", 1, 5000);
        SqliteDataSourceFactory.migrate(ds, "classpath:db/knowledge-pipeline");
        store = new QueryCacheStore(ds);
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void lookupReturnsEmptyForUnknownKey() {
        assertThat(store.lookup("CATEGORY:italian:gcpvj0:1000", "tenant-1")).isEmpty();
    }

    @Test
    void recordAndLookupRoundTrips() {
        var entityIds = List.of("e1", "e2", "e3");
        store.record("TEXT:pizza", "tenant-1", entityIds,
            Instant.now().plusSeconds(3600));
        var entry = store.lookup("TEXT:pizza", "tenant-1");
        assertThat(entry).isPresent();
        assertThat(entry.get().entityIds()).containsExactly("e1", "e2", "e3");
    }

    @Test
    void lookupReturnsEmptyForExpiredEntry() {
        store.record("TEXT:pizza", "tenant-1", List.of("e1"),
            Instant.now().minusSeconds(1));
        assertThat(store.lookup("TEXT:pizza", "tenant-1")).isEmpty();
    }

    @Test
    void differentTenantsAreSeparate() {
        store.record("TEXT:pizza", "t1", List.of("e1"), Instant.now().plusSeconds(3600));
        store.record("TEXT:pizza", "t2", List.of("e2"), Instant.now().plusSeconds(3600));
        assertThat(store.lookup("TEXT:pizza", "t1").get().entityIds())
            .containsExactly("e1");
        assertThat(store.lookup("TEXT:pizza", "t2").get().entityIds())
            .containsExactly("e2");
    }

    @Test
    void entityIdSerializationRoundTrips() {
        var ids = List.of("abc-123", "def-456", "ghi-789");
        String json = QueryCacheStore.serializeEntityIds(ids);
        List<String> parsed = QueryCacheStore.parseEntityIds(json);
        assertThat(parsed).isEqualTo(ids);
    }

    @Test
    void emptyEntityIdsRoundTrips() {
        String json = QueryCacheStore.serializeEntityIds(List.of());
        assertThat(QueryCacheStore.parseEntityIds(json)).isEmpty();
    }
}
