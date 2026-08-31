package io.casehub.neocortex.cognitive;

import java.util.Comparator;
import java.util.List;

public final class RetrievalModulator {

    private RetrievalModulator() {}

    public static <T> List<T> modulate(List<T> items, ModulationProfile<T> profile,
            List<ModulationFactor<T>> factors) {
        if (items.isEmpty() || factors.isEmpty()) return items;
        return items.stream()
            .sorted(Comparator.comparingDouble(
                (T item) -> compositeScore(item, profile, factors)).reversed())
            .toList();
    }

    private static <T> double compositeScore(T item, ModulationProfile<T> profile,
            List<ModulationFactor<T>> factors) {
        double score = 1.0;
        for (var factor : factors) {
            score *= factor.apply(item, profile);
        }
        return score;
    }
}
