package io.casehub.neocortex.caps;

public record SituationActivation(
    String nodeId,
    double confidence,
    int[] source
) {
    public SituationActivation(String nodeId, double confidence) {
        this(nodeId, confidence, null);
    }
}
