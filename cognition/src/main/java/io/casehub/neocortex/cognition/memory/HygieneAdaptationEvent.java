package io.casehub.neocortex.cognition.memory;

import java.time.Instant;

public record HygieneAdaptationEvent(
        String tenantId,
        RetentionConfig previousConfig,
        RetentionConfig newConfig,
        double evictionPressure,
        AdaptationDirection direction,
        Instant timestamp) {

    public enum AdaptationDirection { LOWERED, RAISED }
}
