package io.casehub.neocortex.cognition.mentalmodel;

import java.time.Instant;
import java.util.Objects;

public record AttributedState(
        String key,
        String description,
        double confidence,
        int entrenchment,
        Instant lastReinforced,
        BdiDimension dimension,
        IntentValence valence) {
    public AttributedState {
        Objects.requireNonNull(key, "key required");
        Objects.requireNonNull(description, "description required");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0, 1], got " + confidence);
        }
        if (entrenchment < 0) {throw new IllegalArgumentException("entrenchment must be >= 0");}
        Objects.requireNonNull(lastReinforced, "lastReinforced required");
        Objects.requireNonNull(dimension, "dimension required");
        Objects.requireNonNull(valence, "valence required");
    }

    public AttributedState(String key, String description, double confidence,
                           int entrenchment, Instant lastReinforced, BdiDimension dimension) {
        this(key, description, confidence, entrenchment, lastReinforced, dimension, IntentValence.NEUTRAL);
    }
}
