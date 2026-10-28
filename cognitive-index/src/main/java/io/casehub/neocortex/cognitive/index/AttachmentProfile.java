package io.casehub.neocortex.cognitive.index;

public record AttachmentProfile(
        String style,
        double anxiety,
        double avoidance
) {
    public AttachmentProfile {
        if (anxiety < 0.0 || anxiety > 1.0)
            throw new IllegalArgumentException("anxiety must be in [0,1], got " + anxiety);
        if (avoidance < 0.0 || avoidance > 1.0)
            throw new IllegalArgumentException("avoidance must be in [0,1], got " + avoidance);
    }
}
