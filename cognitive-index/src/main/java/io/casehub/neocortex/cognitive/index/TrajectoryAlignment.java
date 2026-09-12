package io.casehub.neocortex.cognitive.index;

import java.util.Map;

public record TrajectoryAlignment(
    Map<AgentPair, Double> cosineSimilarities,
    Map<AgentPair, TrendAgreement> agreements
) {
    public TrajectoryAlignment {
        cosineSimilarities = Map.copyOf(cosineSimilarities);
        agreements = Map.copyOf(agreements);
    }
}