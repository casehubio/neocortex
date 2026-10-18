package io.casehub.neocortex.memory.cbr.embedding;

import io.casehub.neocortex.inference.DenseEmbedder;
import io.casehub.neocortex.inference.Embeddings;
import io.casehub.neocortex.memory.cbr.LocalSimilarityFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EmbeddingTextSimilarity implements LocalSimilarityFunction {

    private final DenseEmbedder model;
    private final Map<String, float[]> cache = new HashMap<>();

    public EmbeddingTextSimilarity(DenseEmbedder model) {
        this.model = Objects.requireNonNull(model);
    }

    public void precompute(List<String> texts) {
        List<String> uncached = texts.stream()
            .filter(t -> !cache.containsKey(t))
            .distinct()
            .toList();
        if (!uncached.isEmpty()) {
            List<float[]> embeddings = model.embedBatch(uncached);
            for (int i = 0; i < uncached.size(); i++) {
                cache.put(uncached.get(i), embeddings.get(i));
            }
        }
    }

    @Override
    public double compute(io.casehub.neocortex.memory.cbr.FeatureValue queryValue, io.casehub.neocortex.memory.cbr.FeatureValue caseValue) {
        if (queryValue instanceof io.casehub.neocortex.memory.cbr.FeatureValue.StringVal qs
            && caseValue instanceof io.casehub.neocortex.memory.cbr.FeatureValue.StringVal cs) {
            float[] queryEmb = embed(qs.value());
            float[] caseEmb  = embed(cs.value());
            return Math.max(0.0, Embeddings.cosineSimilarity(queryEmb, caseEmb));
        }
        return 0.0;
    }

    private float[] embed(String text) {
        return cache.computeIfAbsent(text, model::embed);
    }
}
