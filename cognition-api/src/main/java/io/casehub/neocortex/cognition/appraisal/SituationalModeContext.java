package io.casehub.neocortex.cognition.appraisal;

import org.jspecify.annotations.Nullable;

public record SituationalModeContext(
        @Nullable String affectiveMode,
        double affectiveIntensity,
        @Nullable String socialMode,
        double socialIntensity
) {
    public boolean hasActiveMode() {
        return affectiveMode != null || socialMode != null;
    }
}
