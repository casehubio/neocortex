package io.casehub.neocortex.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConvexCombinationFusion {

    public record ScoredLeg(List<RetrievedChunk> chunks, double weight) {
        public ScoredLeg {
            Objects.requireNonNull(chunks, "chunks");
            if (weight < 0) throw new IllegalArgumentException("weight must be non-negative");
        }
    }

    private ConvexCombinationFusion() {}

    public static List<RetrievedChunk> fuse(List<ScoredLeg> legs, int maxResults) {
        if (legs.isEmpty()) return List.of();

        // Renormalize weights for active legs
        double totalWeight = legs.stream().mapToDouble(ScoredLeg::weight).sum();
        if (totalWeight <= 0) return List.of();

        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, RetrievedChunk> chunks = new LinkedHashMap<>();

        for (ScoredLeg leg : legs) {
            double normalizedWeight = leg.weight() / totalWeight;
            double[] minMax = minMax(leg.chunks());
            double min = minMax[0], max = minMax[1];
            double range = max - min;

            for (RetrievedChunk chunk : leg.chunks()) {
                String key = dedupKey(chunk);
                double normalized = range > 0
                    ? (chunk.relevanceScore() - min) / range
                    : 1.0;  // all equal → all get 1.0
                scores.merge(key, normalizedWeight * normalized, Double::sum);

                chunks.merge(key, chunk, (existing, incoming) ->
                    betterGrade(existing.grade(), incoming.grade()) == incoming.grade()
                        ? existing.withGrade(incoming.grade()) : existing);
            }
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        List<RetrievedChunk> result = new ArrayList<>(Math.min(sorted.size(), maxResults));
        for (int i = 0; i < Math.min(sorted.size(), maxResults); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            RetrievedChunk original = chunks.get(entry.getKey());
            result.add(new RetrievedChunk(original.content(), original.sourceDocumentId(),
                entry.getValue(), original.metadata(), original.grade()));
        }
        return List.copyOf(result);
    }

    private static String dedupKey(RetrievedChunk c) {
        return c.sourceDocumentId() + "\0" + c.content();
    }

    private static RelevanceGrade betterGrade(RelevanceGrade a, RelevanceGrade b) {
        return gradeRank(a) <= gradeRank(b) ? a : b;
    }

    private static int gradeRank(RelevanceGrade g) {
        return switch (g) {
            case CORRECT -> 0;
            case AMBIGUOUS -> 1;
            case UNGRADED -> 2;
            case INCORRECT -> 3;
        };
    }

    private static double[] minMax(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) return new double[] {0.0, 0.0};
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (RetrievedChunk chunk : chunks) {
            double score = chunk.relevanceScore();
            if (score < min) min = score;
            if (score > max) max = score;
        }
        return new double[] {min, max};
    }
}
