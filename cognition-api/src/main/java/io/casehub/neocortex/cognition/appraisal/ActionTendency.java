package io.casehub.neocortex.cognition.appraisal;

import java.util.Objects;

public record ActionTendency(
        ActionReadiness readiness,
        double intensity,
        String target) {
    public ActionTendency {
        Objects.requireNonNull(readiness, "readiness");
        if (intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("intensity must be in [0, 1]: " + intensity);
        }
        if (target == null) target = "";
    }
}
