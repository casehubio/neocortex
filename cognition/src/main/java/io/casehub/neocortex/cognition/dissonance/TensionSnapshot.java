package io.casehub.neocortex.cognition.dissonance;

import java.util.Map;

public record TensionSnapshot(
        String tensionNodeId,
        String tensionType,
        String elementAId,
        String elementBId,
        String elementAText,
        String elementBText,
        double strength,
        String reasoning,
        Map<String, Double> capsActivationsA,
        Map<String, Double> capsActivationsB
) {}
