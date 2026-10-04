package io.casehub.neocortex.knowledge.cache;

import io.casehub.connectors.location.model.Coordinates;
import io.casehub.neocortex.knowledge.BoundingBox;
import io.casehub.neocortex.knowledge.CacheFilter;
import io.casehub.neocortex.knowledge.CachedEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySpatialCacheStoreTest {

    private InMemorySpatialCacheStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySpatialCacheStore();
    }

    private CachedEntity entity(String id, double lat, double lng) {
        return new CachedEntity(id, "Place " + id, new Coordinates(lat, lng),
            "restaurant", "google", "ext-" + id, Map.of(),
            Instant.now(), Instant.now().plusSeconds(86400), Set.of(), false);
    }

    @Test
    void setAndGetRoundTrips() {
        store.set(entity("e1", 51.5, -0.1), "t1");
        assertThat(store.get("e1", "t1")).isNotNull();
        assertThat(store.get("e1", "t1").name()).isEqualTo("Place e1");
    }

    @Test
    void getReturnsNullForUnknown() {
        assertThat(store.get("nonexistent", "t1")).isNull();
    }

    @Test
    void nearbyReturnsEntitiesWithinRadius() {
        store.set(entity("e1", 51.5, -0.1), "t1");
        store.set(entity("e2", 51.50005, -0.10005), "t1");
        store.set(entity("e3", 55.9, -3.1), "t1");

        var nearby = store.nearby(new Coordinates(51.5, -0.1), 1000,
            CacheFilter.none(), "t1");
        assertThat(nearby).extracting(CachedEntity::id)
            .containsExactlyInAnyOrder("e1", "e2");
    }

    @Test
    void withinReturnsBoundedEntities() {
        store.set(entity("e1", 51.5, -0.1), "t1");
        store.set(entity("e2", 51.6, -0.2), "t1");
        store.set(entity("e3", 55.9, -3.1), "t1");

        var within = store.within(
            new BoundingBox(51.4, -0.3, 51.7, 0.0), CacheFilter.none(), "t1");
        assertThat(within).extracting(CachedEntity::id)
            .containsExactlyInAnyOrder("e1", "e2");
    }

    @Test
    void removeDeletesEntity() {
        store.set(entity("e1", 51.5, -0.1), "t1");
        store.remove("e1", "t1");
        assertThat(store.get("e1", "t1")).isNull();
    }

    @Test
    void expireUpdatesExpiresAt() {
        store.set(entity("e1", 51.5, -0.1), "t1");
        Instant newExpiry = Instant.now().plusSeconds(999999);
        store.expire("e1", newExpiry, "t1");
        assertThat(store.get("e1", "t1").expiresAt()).isEqualTo(newExpiry);
    }

    @Test
    void tenantIsolation() {
        store.set(entity("e1", 51.5, -0.1), "t1");
        store.set(entity("e2", 51.5, -0.1), "t2");
        assertThat(store.get("e1", "t2")).isNull();
        assertThat(store.get("e2", "t1")).isNull();
    }

    @Test
    void nearbyFiltersByCategory() {
        var italian = new CachedEntity("e1", "Ondine", new Coordinates(51.5, -0.1),
            "italian", "google", "ext-1", Map.of(),
            Instant.now(), Instant.now().plusSeconds(86400), Set.of(), false);
        var coffee = new CachedEntity("e2", "Costa", new Coordinates(51.50001, -0.10001),
            "coffee", "google", "ext-2", Map.of(),
            Instant.now(), Instant.now().plusSeconds(86400), Set.of(), false);
        store.set(italian, "t1");
        store.set(coffee, "t1");

        var filtered = store.nearby(new Coordinates(51.5, -0.1), 1000,
            new CacheFilter("italian", null, null, null), "t1");
        assertThat(filtered).extracting(CachedEntity::id).containsExactly("e1");
    }
}
