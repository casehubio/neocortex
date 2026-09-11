package io.casehub.neocortex.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AdaptiveFilter {
    private AdaptiveFilter() {}

    public static <T> List<T> filter(List<T> scored, int requestedLimit,
                                      AdaptiveFilterOptions<T> options) {
        if (scored.isEmpty()) return List.of();

        var config = options.config();
        var extractor = options.scoreExtractor();

        var sorted = scored.stream()
            .sorted(Comparator.comparingDouble(extractor).reversed())
            .toList();

        var floored = new ArrayList<>(sorted.stream()
            .filter(item -> extractor.applyAsDouble(item) >= config.scoreFloor())
            .toList());

        if (options.ceBoundary() != null) {
            for (int i = 1; i < floored.size(); i++) {
                if (options.ceBoundary().test(floored.get(i - 1))
                        && !options.ceBoundary().test(floored.get(i))) {
                    floored.subList(i, floored.size()).clear();
                    break;
                }
            }
        }

        for (int i = 1; i < floored.size(); i++) {
            double gap = extractor.applyAsDouble(floored.get(i - 1))
                       - extractor.applyAsDouble(floored.get(i));
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

        if (options.clusterGapThreshold() > 0 && floored.size() > requestedLimit) {
            int end = requestedLimit;
            while (end < floored.size()) {
                double gap = extractor.applyAsDouble(floored.get(end - 1))
                           - extractor.applyAsDouble(floored.get(end));
                if (gap >= options.clusterGapThreshold()) break;
                end++;
            }
            return floored.subList(0, end);
        }

        return floored.size() > requestedLimit
            ? floored.subList(0, requestedLimit)
            : floored;
    }
}
