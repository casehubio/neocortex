package io.casehub.neocortex.cognitive;

import java.time.Instant;
import java.util.Objects;

public record CognitiveEmotion(
        EmotionType type,
        double intensity,
        String subjectId,
        Instant onset,
        EmotionSource source,
        PadProjection pad
) {
    public CognitiveEmotion {
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(subjectId, "subjectId required");
        Objects.requireNonNull(onset, "onset required");
        Objects.requireNonNull(source, "source required");
        Objects.requireNonNull(pad, "pad required");
        if (intensity < 0.0 || intensity > 1.0)
            throw new IllegalArgumentException("intensity must be in [0, 1], got " + intensity);
    }
}
