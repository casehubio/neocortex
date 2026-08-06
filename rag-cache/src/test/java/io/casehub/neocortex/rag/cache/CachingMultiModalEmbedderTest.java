package io.casehub.neocortex.rag.cache;

import io.casehub.neocortex.inference.EmbeddingMode;
import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CachingMultiModalEmbedderTest {

    private EmbeddingCache cache;
    private CountingEmbedder delegate;
    private CachingMultiModalEmbedder caching;

    @BeforeEach
    void setUp() {
        cache = new EmbeddingCache(":memory:", "test:3:512:");
        cache.init();
        delegate = new CountingEmbedder();
        caching = new CachingMultiModalEmbedder(delegate, cache, true);
    }

    @AfterEach
    void tearDown() {
        cache.shutdown();
    }

    @Test
    void cacheMissCallsDelegate() {
        MultiModalEmbedding result = caching.embed("hello");
        assertNotNull(result);
        assertEquals(1, delegate.callCount());
    }

    @Test
    void cacheHitSkipsDelegate() {
        caching.embed("hello");
        assertEquals(1, delegate.callCount());

        caching.embed("hello");
        assertEquals(1, delegate.callCount());
    }

    @Test
    void embedBatchSplitsHitsAndMisses() {
        caching.embed("a");
        assertEquals(1, delegate.callCount());

        List<MultiModalEmbedding> results =
                caching.embedBatch(List.of("a", "b", "c"));

        assertEquals(3, results.size());
        assertEquals(2, delegate.callCount());
    }

    @Test
    void disabledPassesThrough() {
        var disabled = new CachingMultiModalEmbedder(delegate, cache, false);
        disabled.embed("x");
        disabled.embed("x");
        assertEquals(2, delegate.callCount());
    }

    @Test
    void passthroughMethods() {
        assertEquals(3, caching.denseDimension());
        assertEquals(512, caching.maxSequenceLength());
        assertTrue(caching.supportedModes().contains(EmbeddingMode.DENSE));
    }

    @Test
    void allMissesInBatch() {
        List<MultiModalEmbedding> results =
                caching.embedBatch(List.of("x", "y", "z"));
        assertEquals(3, results.size());
        assertEquals(1, delegate.callCount());
    }

    @Test
    void allHitsInBatch() {
        caching.embedBatch(List.of("x", "y", "z"));
        assertEquals(1, delegate.callCount());

        caching.embedBatch(List.of("x", "y", "z"));
        assertEquals(1, delegate.callCount());
    }

    static class CountingEmbedder implements MultiModalEmbedder {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public MultiModalEmbedding embed(String text) {
            calls.incrementAndGet();
            return new MultiModalEmbedding(new float[]{1.0f, 2.0f, 3.0f}, null, null);
        }

        @Override
        public List<MultiModalEmbedding> embedBatch(List<String> texts) {
            calls.incrementAndGet();
            return texts.stream()
                    .map(t -> new MultiModalEmbedding(new float[]{1.0f, 2.0f, 3.0f}, null, null))
                    .toList();
        }

        @Override
        public Set<EmbeddingMode> supportedModes() {
            return Set.of(EmbeddingMode.DENSE);
        }

        @Override
        public int denseDimension() { return 3; }

        @Override
        public OptionalInt colbertDimension() { return OptionalInt.empty(); }

        @Override
        public int maxSequenceLength() { return 512; }

        int callCount() { return calls.get(); }
    }
}
