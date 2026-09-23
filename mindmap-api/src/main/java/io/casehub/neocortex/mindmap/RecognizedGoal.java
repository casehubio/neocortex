package io.casehub.neocortex.mindmap;

import java.util.Objects;

public record RecognizedGoal(
        String description,
        String origin,
        String suggestedHorizon,
        double confidence) {
    public RecognizedGoal {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(origin, "origin");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
    }
}
