package io.casehub.neocortex.cognition.appraisal;

import java.util.Objects;

public record Drive(
        String name,
        DriveCategory category,
        double intensity,
        String trigger) {
    public Drive {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        if (intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("intensity must be in [0, 1]: " + intensity);
        }
        if (trigger == null) trigger = "";
    }
}
