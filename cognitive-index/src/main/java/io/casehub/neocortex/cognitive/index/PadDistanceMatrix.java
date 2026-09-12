package io.casehub.neocortex.cognitive.index;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.Map;

public record PadDistanceMatrix(Map<AgentPair, Double> distances) {
    public PadDistanceMatrix { distances = Map.copyOf(distances); }

    public double distance(PrincipalId a, PrincipalId b) {
        return distances.getOrDefault(AgentPair.of(a, b), 0.0);
    }

    public double maxDistance() {
        return distances.values().stream().mapToDouble(d -> d).max().orElse(0.0);
    }

    public double meanDistance() {
        return distances.values().stream().mapToDouble(d -> d).average().orElse(0.0);
    }
}