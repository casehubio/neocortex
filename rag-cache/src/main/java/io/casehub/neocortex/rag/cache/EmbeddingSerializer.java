package io.casehub.neocortex.rag.cache;

import io.casehub.neocortex.inference.MultiModalEmbedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EmbeddingSerializer {

    private EmbeddingSerializer() {}

    public static byte[] serializeDense(MultiModalEmbedding embedding) {
        float[] dense = embedding.dense();
        ByteBuffer buf = ByteBuffer.allocate(4 + dense.length * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(dense.length);
        for (float f : dense) buf.putFloat(f);
        return buf.array();
    }

    public static byte[] serializeSparse(MultiModalEmbedding embedding) {
        Map<Integer, Float> sparse = embedding.sparse();
        if (sparse == null) return null;
        ByteBuffer buf = ByteBuffer.allocate(4 + sparse.size() * 8)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(sparse.size());
        for (var entry : sparse.entrySet()) {
            buf.putInt(entry.getKey());
            buf.putFloat(entry.getValue());
        }
        return buf.array();
    }

    public static byte[] serializeColbert(MultiModalEmbedding embedding) {
        float[][] colbert = embedding.colbert();
        if (colbert == null) return null;
        int rows = colbert.length;
        int cols = rows > 0 ? colbert[0].length : 0;
        ByteBuffer buf = ByteBuffer.allocate(8 + rows * cols * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(rows);
        buf.putInt(cols);
        for (float[] row : colbert) {
            for (float f : row) buf.putFloat(f);
        }
        return buf.array();
    }

    public static MultiModalEmbedding deserialize(byte[] denseBytes,
                                                    byte[] sparseBytes,
                                                    byte[] colbertBytes) {
        float[] dense = deserializeDense(denseBytes);
        Map<Integer, Float> sparse = deserializeSparse(sparseBytes);
        float[][] colbert = deserializeColbert(colbertBytes);
        return new MultiModalEmbedding(dense, sparse, colbert);
    }

    private static float[] deserializeDense(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int length = buf.getInt();
        float[] dense = new float[length];
        for (int i = 0; i < length; i++) dense[i] = buf.getFloat();
        return dense;
    }

    private static Map<Integer, Float> deserializeSparse(byte[] bytes) {
        if (bytes == null) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int size = buf.getInt();
        Map<Integer, Float> sparse = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            sparse.put(buf.getInt(), buf.getFloat());
        }
        return sparse;
    }

    private static float[][] deserializeColbert(byte[] bytes) {
        if (bytes == null) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int rows = buf.getInt();
        int cols = buf.getInt();
        float[][] colbert = new float[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                colbert[r][c] = buf.getFloat();
            }
        }
        return colbert;
    }
}
