package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.inference.DenseEmbedder;
import io.casehub.neocortex.memory.Memory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class TextSimilarityCorroborator {

    private final DenseEmbedder embeddingModel;
    private final double embeddingThreshold;
    private final double keywordThreshold;

    TextSimilarityCorroborator(DenseEmbedder embeddingModel,
                               double embeddingThreshold,
                               double keywordThreshold) {
        this.embeddingModel = embeddingModel;
        this.embeddingThreshold = embeddingThreshold;
        this.keywordThreshold = keywordThreshold;
    }

    int countSimilar(Memory target, List<Memory> candidates) {
        if (candidates.isEmpty() || target.text() == null || target.text().isBlank()) {
            return 0;
        }
        if (embeddingModel != null) {
            return countByEmbedding(target, candidates);
        }
        return countByKeywordOverlap(target, candidates);
    }

    private int countByEmbedding(Memory target, List<Memory> candidates) {
        float[] targetEmbedding = embeddingModel.embed(target.text());
        int count = 0;
        for (Memory candidate : candidates) {
            if (candidate.memoryId().equals(target.memoryId())) continue;
            if (candidate.text() == null || candidate.text().isBlank()) continue;
            float[] candidateEmbedding = embeddingModel.embed(candidate.text());
            double similarity = cosineSimilarity(targetEmbedding, candidateEmbedding);
            if (similarity >= embeddingThreshold) {
                count++;
            }
        }
        return count;
    }

    int countByKeywordOverlap(Memory target, List<Memory> candidates) {
        Set<String> targetTokens = tokenize(target.text());
        if (targetTokens.isEmpty()) return 0;

        int count = 0;
        for (Memory candidate : candidates) {
            if (candidate.memoryId().equals(target.memoryId())) continue;
            if (candidate.text() == null || candidate.text().isBlank()) continue;
            Set<String> candidateTokens = tokenize(candidate.text());
            double jaccard = jaccardSimilarity(targetTokens, candidateTokens);
            if (jaccard >= keywordThreshold) {
                count++;
            }
        }
        return count;
    }

    static Set<String> tokenize(String text) {
        String[] words = text.toLowerCase(Locale.ROOT).split("\\W+");
        Set<String> tokens = new HashSet<>();
        for (String w : words) {
            if (w.length() > 2) tokens.add(w);
        }
        return tokens;
    }

    static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }
}
