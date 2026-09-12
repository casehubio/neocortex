package io.casehub.neocortex.cognitive.index;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.Map;

public record PairwiseDifferences(Map<AgentPair, Double> differences) {
    public PairwiseDifferences {differences = Map.copyOf(differences);}

    public double difference(PrincipalId a, PrincipalId b) {
        AgentPair pair = AgentPair.of(a, b);
        double    raw  = differences.getOrDefault(pair, 0.0);
        return a.value().compareTo(b.value()) <= 0 ? raw : -raw;
    }
}