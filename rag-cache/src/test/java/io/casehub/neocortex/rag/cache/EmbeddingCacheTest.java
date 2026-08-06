package io.casehub.neocortex.rag.cache;

import io.casehub.neocortex.inference.MultiModalEmbedding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingCacheTest {

    private EmbeddingCache cache;

    @BeforeEach
    void setUp() {
        cache = new EmbeddingCache(":memory:", "test-model:1024:768:");
        cache.init();
    }

    @AfterEach
    void tearDown() {
        cache.shutdown();
    }

    @Test
    void putAndGet() {
        var embedding = new MultiModalEmbedding(
                new float[]{1.0f, 2.0f}, null, null);
        cache.put("abc123", embedding);

        var result = cache.get("abc123");
        assertTrue(result.isPresent());
        assertArrayEquals(new float[]{1.0f, 2.0f}, result.get().dense(), 1e-6f);
    }

    @Test
    void getMissReturnsEmpty() {
        var result = cache.get("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void modelVersionIsolation() {
        var embedding = new MultiModalEmbedding(
                new float[]{1.0f}, null, null);
        cache.put("hash1", embedding);

        var otherCache = new EmbeddingCache(":memory:", "other-model:512:256:");
        otherCache.init();
        try {
            assertTrue(otherCache.get("hash1").isEmpty());
        } finally {
            otherCache.shutdown();
        }
    }

    @Test
    void getBatchReturnsOnlyHits() {
        var e1 = new MultiModalEmbedding(new float[]{1.0f}, null, null);
        var e2 = new MultiModalEmbedding(new float[]{2.0f}, null, null);
        cache.put("h1", e1);
        cache.put("h2", e2);

        Map<String, MultiModalEmbedding> result =
                cache.getBatch(List.of("h1", "h2", "h3"));

        assertEquals(2, result.size());
        assertTrue(result.containsKey("h1"));
        assertTrue(result.containsKey("h2"));
        assertFalse(result.containsKey("h3"));
    }

    @Test
    void putBatch() {
        var e1 = new MultiModalEmbedding(new float[]{1.0f}, null, null);
        var e2 = new MultiModalEmbedding(new float[]{2.0f}, null, null);
        cache.putBatch(Map.of("h1", e1, "h2", e2));

        assertTrue(cache.get("h1").isPresent());
        assertTrue(cache.get("h2").isPresent());
    }

    @Test
    void fullRoundTripWithSparseAndColbert() {
        var embedding = new MultiModalEmbedding(
                new float[]{0.1f, 0.2f},
                Map.of(5, 1.5f, 100, 0.8f),
                new float[][]{{0.3f, 0.4f}, {0.5f, 0.6f}});
        cache.put("full", embedding);

        var result = cache.get("full").orElseThrow();
        assertArrayEquals(new float[]{0.1f, 0.2f}, result.dense(), 1e-6f);
        assertEquals(1.5f, result.sparse().get(5), 1e-6f);
        assertArrayEquals(new float[]{0.3f, 0.4f}, result.colbert()[0], 1e-6f);
    }
}
