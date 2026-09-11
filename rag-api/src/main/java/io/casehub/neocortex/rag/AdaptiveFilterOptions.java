package io.casehub.neocortex.rag;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public record AdaptiveFilterOptions<T>(
    AdaptiveSearchConfig config,
    ToDoubleFunction<T> scoreExtractor,
    Predicate<T> ceBoundary,
    double clusterGapThreshold
) {
    public AdaptiveFilterOptions {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(scoreExtractor, "scoreExtractor must not be null");
        if (clusterGapThreshold < 0 || clusterGapThreshold > 1)
            throw new IllegalArgumentException("clusterGapThreshold must be in [0,1]");
    }

    public static <T> AdaptiveFilterOptions<T> of(AdaptiveSearchConfig config,
                                                    ToDoubleFunction<T> scoreExtractor) {
        return new AdaptiveFilterOptions<>(config, scoreExtractor, null, 0.0);
    }

    public AdaptiveFilterOptions<T> withCeBoundary(Predicate<T> ceBoundary) {
        return new AdaptiveFilterOptions<>(config, scoreExtractor, ceBoundary, clusterGapThreshold);
    }

    public AdaptiveFilterOptions<T> withClusterExtension(double clusterGapThreshold) {
        return new AdaptiveFilterOptions<>(config, scoreExtractor, ceBoundary, clusterGapThreshold);
    }
}
