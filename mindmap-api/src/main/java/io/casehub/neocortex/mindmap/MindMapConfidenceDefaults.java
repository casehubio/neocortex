package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;

import java.time.Instant;

public final class MindMapConfidenceDefaults {

    private MindMapConfidenceDefaults() {}

    public static double defaultValue(ConfidenceOrigin origin) {
        return switch (origin) {
            case STATED -> 1.0;
            case INFERRED -> 0.7;
            case SPECULATED -> 0.3;
            case UNKNOWN -> 1.0;
        };
    }

    public static Confidence forOrigin(ConfidenceOrigin origin, Instant decayReference) {
        return switch (origin) {
            case STATED -> Confidence.stated(defaultValue(origin), decayReference);
            case INFERRED -> Confidence.inferred(defaultValue(origin), decayReference);
            case SPECULATED -> Confidence.speculated(defaultValue(origin), decayReference);
            case UNKNOWN -> Confidence.unknown(defaultValue(origin));
        };
    }
}
