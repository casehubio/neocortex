package io.casehub.neocortex.inference;

import java.util.ArrayList;
import java.util.List;

public final class Embeddings {

    private Embeddings() {}

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have equal length: " + a.length + " vs " + b.length);
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    public static List<Float> toFloatList(float[] vector) {
        var list = new ArrayList<Float>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return List.copyOf(list);
    }
}
