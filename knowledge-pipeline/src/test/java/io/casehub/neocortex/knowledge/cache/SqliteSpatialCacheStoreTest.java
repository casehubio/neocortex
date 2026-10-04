package io.casehub.neocortex.knowledge.cache;

import io.casehub.connectors.location.model.Coordinates;
import io.casehub.neocortex.knowledge.BoundingBox;
import io.casehub.neocortex.knowledge.CacheFilter;
import io.casehub.neocortex.knowledge.CachedEntity;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteSpatialCacheStoreTest {

    private HikariDataSource ds;
    private SqliteSpatialCacheStore store;

    @BeforeEach
    void setUp() {
        ds = SqliteDataSourceFactory.create(":memory:", 3, 5000);
        SqliteDataSourceFactory.migrate(ds, "classpath:db/knowledge-pipeline");
        store = new SqliteSpatialCacheStore(ds);
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void setAndGetRoundTrips() {
        var entity = entity("e1", "Ondine", 55.9533, -3.1883, "restaurant");
        store.set(entity, "t1");

        var result = store.get("e1", "t1");
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Ondine");
        assertThat(result.coordinates().lat()).isEqualTo(55.9533);
        assertThat(result.coordinates().lng()).isEqualTo(-3.1883);
        assertThat(result.category()).isEqualTo("restaurant");
        assertThat(result.source()).isEqualTo("google");
        assertThat(result.externalId()).isEqualTo("ext-e1");
    }

    @Test
    void getReturnsNullForMissingEntity() {
        assertThat(store.get("missing", "t1")).isNull();
    }

    @Test
    void nearbyFindsEntitiesWithinRadius() {
        store.set(entity("e1", "Ondine", 55.9533, -3.1883, "restaurant"), "t1");
        store.set(entity("e2", "Castle", 55.9486, -3.1999, "attraction"), "t1");
        store.set(entity("e3", "Far Away", 56.5, -4.0, "restaurant"), "t1");

        var results = store.nearby(
            new Coordinates(55.9500, -3.1900), 1000,
            CacheFilter.none(), "t1");

        assertThat(results).extracting("name")
            .contains("Ondine", "Castle")
            .doesNotContain("Far Away");
    }

    @Test
    void nearbyFiltersByCategory() {
        store.set(entity("e1", "Ondine", 55.9533, -3.1883, "restaurant"), "t1");
        store.set(entity("e2", "Castle", 55.9486, -3.1999, "attraction"), "t1");

        var results = store.nearby(
            new Coordinates(55.9500, -3.1900), 2000,
            new CacheFilter("restaurant", null, null, null), "t1");

        assertThat(results).extracting("name")
            .containsExactly("Ondine");
    }

    @Test
    void withinFindEntitiesInBoundingBox() {
        store.set(entity("e1", "Ondine", 55.9533, -3.1883, "restaurant"), "t1");
        store.set(entity("e2", "Far Away", 56.5, -4.0, "restaurant"), "t1");

        var results = store.within(
            new BoundingBox(55.9, -3.3, 56.0, -3.0),
            CacheFilter.none(), "t1");

        assertThat(results).extracting("name")
            .containsExactly("Ondine");
    }

    @Test
    void removeDeletesEntity() {
        store.set(entity("e1", "Ondine", 55.9533, -3.1883, "restaurant"), "t1");
        store.remove("e1", "t1");

        assertThat(store.get("e1", "t1")).isNull();
        assertThat(store.nearby(new Coordinates(55.9533, -3.1883), 100,
            CacheFilter.none(), "t1")).isEmpty();
    }

    @Test
    void expireUpdatesExpiresAt() {
        var entity = entity("e1", "Ondine", 55.9533, -3.1883, "restaurant");
        store.set(entity, "t1");

        Instant newExpiry = Instant.now().plus(Duration.ofDays(60));
        store.expire("e1", newExpiry, "t1");

        var result = store.get("e1", "t1");
        assertThat(result.expiresAt()).isEqualTo(newExpiry);
    }

    @Test
    void findExpiredReturnsOnlyExpiredEntities() {
        store.set(entity("e1", "Expired", 55.9, -3.1, "restaurant"), "t1");
        var valid = new CachedEntity("e2", "Valid",
            new Coordinates(55.9, -3.1), "restaurant",
            "google", "ext-e2", Map.of(),
            Instant.now(), Instant.now().plus(Duration.ofDays(30)),
            Set.of(), false);
        store.set(valid, "t1");

        var expired = store.findExpired("t1", Instant.now());
        assertThat(expired).containsExactly("e1");
    }

    @Test
    void discoverTenantsReturnsAllTenants() {
        store.set(entity("e1", "A", 55.9, -3.1, "restaurant"), "t1");
        store.set(entity("e2", "B", 51.5, -0.1, "restaurant"), "t2");

        assertThat(store.discoverTenants()).containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    void tenantIsolation() {
        store.set(entity("e1", "Ondine", 55.9533, -3.1883, "restaurant"), "t1");
        store.set(entity("e2", "Other", 55.9533, -3.1883, "restaurant"), "t2");

        assertThat(store.get("e1", "t2")).isNull();
        assertThat(store.nearby(new Coordinates(55.9533, -3.1883), 100,
            CacheFilter.none(), "t1"))
            .extracting("name").containsExactly("Ondine");
    }

    @Test
    void setUpdatesExistingEntity() {
        store.set(entity("e1", "Ondine", 55.9533, -3.1883, "restaurant"), "t1");
        var updated = new CachedEntity("e1", "Ondine Seafood",
            new Coordinates(55.9534, -3.1884), "restaurant",
            "google", "ext-e1", Map.of("phone", "0131 226 1888"),
            Instant.now(), Instant.now().plus(Duration.ofDays(30)),
            Set.of(), true);
        store.set(updated, "t1");

        var result = store.get("e1", "t1");
        assertThat(result.name()).isEqualTo("Ondine Seafood");
        assertThat(result.hasDetail()).isTrue();
        assertThat(result.properties()).containsEntry("phone", "0131 226 1888");
    }

    private CachedEntity entity(String id, String name, double lat, double lng,
                                  String category) {
        return new CachedEntity(id, name, new Coordinates(lat, lng), category,
            "google", "ext-" + id, Map.of(),
            Instant.now(), Instant.now().minus(Duration.ofHours(1)),
            Set.of(), false);
    }
}
