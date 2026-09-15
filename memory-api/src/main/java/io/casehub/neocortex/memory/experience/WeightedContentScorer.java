package io.casehub.neocortex.memory.experience;

import java.util.Objects;

public record WeightedContentScorer(ContentScorer scorer, double weight) {
    public WeightedContentScorer {
        Objects.requireNonNull(scorer, "scorer required");
        if (weight <= 0.0) {
            throw new IllegalArgumentException("weight must be positive, got " + weight);
        }
    }
}
