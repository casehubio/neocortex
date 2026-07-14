package io.casehub.neocortex.memory.cbr;

import java.util.List;
import java.util.Objects;

public record AdaptedPlan(
    List<AdaptedStep> steps
) {
    public AdaptedPlan {
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
    }
}
