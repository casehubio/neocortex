package io.casehub.neocortex.memory.cbr;

import java.util.Objects;

public record GuidanceStep(
    String description,
    String preconditions,
    String expectedOutcome,
    String automationHint
) {
    public GuidanceStep {
        Objects.requireNonNull(description, "description must not be null");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
    }
}
