package io.casehub.neocortex.memory.experience;

import java.util.List;
import java.util.Objects;

@FunctionalInterface
public interface ContentScorer {
    double score(ScoreableContent content);

    static WeightedContentScorer weighted(ContentScorer scorer, double weight) {
        return new WeightedContentScorer(scorer, weight);
    }

    static ContentScorer composite(List<WeightedContentScorer> scorers) {
        Objects.requireNonNull(scorers, "scorers required");
        if (scorers.isEmpty()) {
            throw new IllegalArgumentException("at least one scorer required");
        }
        var copy = List.copyOf(scorers);
        return content -> {
            double weightedSum = 0.0;
            double totalWeight = 0.0;
            for (var ws : copy) {
                weightedSum += ws.scorer().score(content) * ws.weight();
                totalWeight += ws.weight();
            }
            return Math.clamp(weightedSum / totalWeight, 0.0, 1.0);
        };
    }
}
