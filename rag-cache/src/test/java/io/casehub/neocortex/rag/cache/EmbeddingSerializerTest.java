package io.casehub.neocortex.rag.cache;

import io.casehub.neocortex.inference.MultiModalEmbedding;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingSerializerTest {

    @Test
    void roundTripDenseOnly() {
        float[] dense = {1.0f, 2.0f, 3.0f};
        var original = new MultiModalEmbedding(dense, null, null);

        byte[] denseBytes = EmbeddingSerializer.serializeDense(original);
        var restored = EmbeddingSerializer.deserialize(denseBytes, null, null);

        assertArrayEquals(dense, restored.dense(), 1e-6f);
        assertNull(restored.sparse());
        assertNull(restored.colbert());
    }

    @Test
    void roundTripFull() {
        float[] dense = {0.1f, 0.2f, 0.3f, 0.4f};
        Map<Integer, Float> sparse = Map.of(5, 1.5f, 100, 0.8f);
        float[][] colbert = {{0.1f, 0.2f}, {0.3f, 0.4f}, {0.5f, 0.6f}};
        var original = new MultiModalEmbedding(dense, sparse, colbert);

        byte[] denseBytes = EmbeddingSerializer.serializeDense(original);
        byte[] sparseBytes = EmbeddingSerializer.serializeSparse(original);
        byte[] colbertBytes = EmbeddingSerializer.serializeColbert(original);

        var restored = EmbeddingSerializer.deserialize(denseBytes, sparseBytes, colbertBytes);

        assertArrayEquals(dense, restored.dense(), 1e-6f);
        assertEquals(sparse.size(), restored.sparse().size());
        assertEquals(1.5f, restored.sparse().get(5), 1e-6f);
        assertEquals(0.8f, restored.sparse().get(100), 1e-6f);
        assertEquals(3, restored.colbert().length);
        assertArrayEquals(new float[]{0.1f, 0.2f}, restored.colbert()[0], 1e-6f);
    }

    @Test
    void largeDenseArray() {
        float[] dense = new float[1024];
        for (int i = 0; i < 1024; i++) dense[i] = i * 0.001f;
        var original = new MultiModalEmbedding(dense, null, null);

        byte[] bytes = EmbeddingSerializer.serializeDense(original);
        var restored = EmbeddingSerializer.deserialize(bytes, null, null);

        assertArrayEquals(dense, restored.dense(), 1e-6f);
        assertEquals(4 + 1024 * 4, bytes.length);
    }
}
