package io.casehub.neocortex.knowledge.cache;

import com.zaxxer.hikari.HikariDataSource;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMetadataStoreTest {

    private HikariDataSource ds;
    private EntityMetadataStore store;

    @BeforeEach
    void setUp() {
        ds = SqliteDataSourceFactory.create(":memory:", 1, 5000);
        SqliteDataSourceFactory.migrate(ds, "classpath:db/knowledge-pipeline");
        store = new EntityMetadataStore(ds);
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    private EntityMetadataStore.EntityMetadata testEntity(String id) {
        return new EntityMetadataStore.EntityMetadata(
            id, "Test Place", "restaurant", "google", "ext-" + id,
            "{\"phone\":\"+44 131 226 1888\"}", Instant.now(), null, false);
    }

    @Test
    void saveAndGetRoundTrips() {
        var entity = testEntity("e1");
        store.save(entity);
        var loaded = store.get("e1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo("Test Place");
        assertThat(loaded.get().category()).isEqualTo("restaurant");
    }

    @Test
    void getReturnsEmptyForUnknown() {
        assertThat(store.get("nonexistent")).isEmpty();
    }

    @Test
    void getBatchReturnsMappedEntities() {
        store.save(testEntity("e1"));
        store.save(testEntity("e2"));
        store.save(testEntity("e3"));
        Map<String, EntityMetadataStore.EntityMetadata> batch =
            store.getBatch(List.of("e1", "e3"));
        assertThat(batch).hasSize(2);
        assertThat(batch).containsKey("e1");
        assertThat(batch).containsKey("e3");
    }

    @Test
    void deleteRemovesEntityAndSessions() {
        store.save(testEntity("e1"));
        store.addSession("e1", "session-1");
        store.delete("e1");
        assertThat(store.get("e1")).isEmpty();
        assertThat(store.sessionsFor("e1")).isEmpty();
    }

    @Test
    void addSessionAndSessionsForRoundTrips() {
        store.save(testEntity("e1"));
        store.addSession("e1", "s1");
        store.addSession("e1", "s2");
        store.addSession("e1", "s1");
        Set<String> sessions = store.sessionsFor("e1");
        assertThat(sessions).containsExactlyInAnyOrder("s1", "s2");
    }
}
