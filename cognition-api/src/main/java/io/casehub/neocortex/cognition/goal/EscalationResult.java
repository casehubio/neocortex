package io.casehub.neocortex.cognition.goal;

import io.casehub.eidos.api.GoalPriority;

import java.util.Objects;

public record EscalationResult(
        GoalPriority priority,
        String themeLabel,
        String reason) {
    public EscalationResult {
        Objects.requireNonNull(priority);
        Objects.requireNonNull(themeLabel);
        Objects.requireNonNull(reason);
    }
}
