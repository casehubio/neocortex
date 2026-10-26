package io.casehub.neocortex.cognition.prompt.observation;

import java.util.Objects;

public record DriveSnapshot(String type, double intensity, String description, String context) {
    public DriveSnapshot {
        Objects.requireNonNull(type);
        if (description == null) { description = ""; }
        if (context == null) { context = "any"; }
        if (intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("intensity must be in [0,1], got " + intensity);
        }
    }

    public DriveSnapshot(String type, double intensity, String description) {
        this(type, intensity, description, "any");
    }
}
