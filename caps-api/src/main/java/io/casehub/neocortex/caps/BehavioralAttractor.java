package io.casehub.neocortex.caps;

public record BehavioralAttractor(
    String category,
    String nodeId,
    double strength,
    boolean highSaturation,
    long sourceGeneration
) {}
