package io.casehub.neocortex.cognitive;

import java.time.Instant;
import java.util.function.Function;

public record ModulationProfile<T>(
    Function<T, Confidence> confidence,
    Function<T, Double> pleasure,
    Function<T, Double> arousal,
    Function<T, Double> dominance,
    Function<T, Instant> timestamp
) {}
