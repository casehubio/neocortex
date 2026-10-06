package io.casehub.neocortex.cognition.gut;

import org.jspecify.annotations.Nullable;

public record GutFeeling(
    GutValence valence,
    double intensity,
    @Nullable String resonanceDescription
) {
    public GutFeeling {
        if (intensity < 0.0 || intensity > 1.0)
            throw new IllegalArgumentException("intensity must be in [0,1], got: " + intensity);
    }
}
