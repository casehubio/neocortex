package io.casehub.neocortex.cognition.dissonance;

public record TensionSnapshot(
    String tensionNodeId,
    String tensionType,
    String elementAId,
    String elementBId,
    String elementAText,
    String elementBText,
    double strength,
    String reasoning
) {}
