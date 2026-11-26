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
        IntentValence valence,
        double originalConfidence) {
    public AttributedState {
        Objects.requireNonNull(key, "key required");
        Objects.requireNonNull(description, "description required");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0, 1], got " + confidence);
        }
        if (originalConfidence < 0.0 || originalConfidence > 1.0) {
            throw new IllegalArgumentException("originalConfidence must be in [0, 1], got " + originalConfidence);
        }
        if (entrenchment < 0) {throw new IllegalArgumentException("entrenchment must be >= 0");}
        Objects.requireNonNull(lastReinforced, "lastReinforced required");
        Objects.requireNonNull(dimension, "dimension required");
        Objects.requireNonNull(valence, "valence required");
    }

    public AttributedState(String key, String description, double confidence,
                           int entrenchment, Instant lastReinforced,
                           BdiDimension dimension, IntentValence valence) {
        this(key, description, confidence, entrenchment, lastReinforced, dimension, valence, confidence);
    }

    public AttributedState(String key, String description, double confidence,
                           int entrenchment, Instant lastReinforced, BdiDimension dimension) {
        this(key, description, confidence, entrenchment, lastReinforced, dimension, IntentValence.NEUTRAL);
    }
}
