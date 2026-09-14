package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;

import java.util.Map;
import java.util.Objects;

public record GraduationResult(
    String cognitiveKind,
    ConfidenceOrigin confidenceOrigin,
    Map<String, String> properties
) {
    public GraduationResult {
        Objects.requireNonNull(cognitiveKind, "cognitiveKind required");
        Objects.requireNonNull(confidenceOrigin, "confidenceOrigin required");
        if (properties == null) properties = Map.of();
    }
}
