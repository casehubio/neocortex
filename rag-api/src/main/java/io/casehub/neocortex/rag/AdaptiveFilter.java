package io.casehub.neocortex.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AdaptiveFilter {
    private AdaptiveFilter() {}

    public static List<RetrievedChunk> filter(List<RetrievedChunk> scored,
                                               int requestedLimit,
                                               AdaptiveSearchConfig config) {
        if (scored.isEmpty()) return List.of();

        var sorted = scored.stream()
            .sorted(Comparator.comparingDouble(RetrievedChunk::relevanceScore).reversed())
            .toList();

        var floored = new ArrayList<>(sorted.stream()
            .filter(c -> c.relevanceScore() >= config.scoreFloor())
            .toList());

        for (int i = 1; i < floored.size(); i++) {
            double gap = floored.get(i - 1).relevanceScore() - floored.get(i).relevanceScore();
            if (gap >= config.gapThreshold()) {
                floored.subList(i, floored.size()).clear();
                break;
            }
        }

        if (floored.size() < config.minResults() && sorted.size() >= config.minResults()) {
            return sorted.subList(0, Math.min(config.minResults(), sorted.size()));
        }
        if (floored.size() < config.minResults()) {
            return sorted;
        }

        return floored.size() > requestedLimit
            ? floored.subList(0, requestedLimit)
            : floored;
    }
}
