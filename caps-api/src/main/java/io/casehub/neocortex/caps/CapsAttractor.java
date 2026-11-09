package io.casehub.neocortex.caps;

public record CapsAttractor(
        String category,
        String nodeId,
        double strength,
        boolean highSaturation,
        long sourceGeneration,
        String personalityLabel
) {
    public CapsAttractor(String category, String nodeId, double strength,
                         boolean highSaturation, long sourceGeneration) {
        this(category, nodeId, strength, highSaturation, sourceGeneration, null);
    }
}
