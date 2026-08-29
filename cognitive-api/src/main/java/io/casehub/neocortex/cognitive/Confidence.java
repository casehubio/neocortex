package io.casehub.neocortex.cognitive;

import java.time.Instant;
import java.util.Objects;

public record Confidence(
    ConfidenceOrigin origin,
    double value,
    Instant decayReference
) {
    public Confidence {
        Objects.requireNonNull(origin, "origin required");
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException("value must be in [0,1], got: " + value);
        }
    }

    public static Confidence stated(double value, Instant decayReference) {
        Objects.requireNonNull(decayReference, "MindMap confidences require a decayReference");
        return new Confidence(ConfidenceOrigin.STATED, value, decayReference);
    }

    public static Confidence inferred(double value, Instant decayReference) {
        Objects.requireNonNull(decayReference, "MindMap confidences require a decayReference");
        return new Confidence(ConfidenceOrigin.INFERRED, value, decayReference);
    }

    public static Confidence speculated(double value, Instant decayReference) {
        Objects.requireNonNull(decayReference, "MindMap confidences require a decayReference");
        return new Confidence(ConfidenceOrigin.SPECULATED, value, decayReference);
    }

    public static Confidence unknown(double value) {
        return new Confidence(ConfidenceOrigin.UNKNOWN, value, null);
    }

    public Confidence withValue(double newValue) {
        return new Confidence(origin, newValue, decayReference);
    }

    public Confidence withDecayReference(Instant newRef) {
        return new Confidence(origin, value, newRef);
    }
}
