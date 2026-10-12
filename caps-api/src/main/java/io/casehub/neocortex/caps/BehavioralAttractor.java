package io.casehub.neocortex.caps;

public record BehavioralAttractor(
        String category,
        String nodeId,
        double strength,
        boolean highSaturation,
        long sourceGeneration,
        String personalityLabel
) {
    public BehavioralAttractor(String category, String nodeId, double strength,
                               boolean highSaturation, long sourceGeneration) {
        this(category, nodeId, strength, highSaturation, sourceGeneration, null);
    }
}
