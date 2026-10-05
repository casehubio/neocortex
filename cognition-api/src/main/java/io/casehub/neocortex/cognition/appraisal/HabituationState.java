package io.casehub.neocortex.cognition.appraisal;

import java.util.HashMap;
import java.util.Map;

public record HabituationState(
        Map<String, Integer> observationCounts,
        Map<String, Double> noveltyScores) {
    public HabituationState {
        observationCounts = observationCounts != null ? Map.copyOf(observationCounts) : Map.of();
        noveltyScores = noveltyScores != null ? Map.copyOf(noveltyScores) : Map.of();
    }

    public static HabituationState empty() {
        return new HabituationState(Map.of(), Map.of());
    }

    public HabituationState withObservation(String hash, double novelty) {
        var counts = new HashMap<>(observationCounts);
        counts.merge(hash, 1, Integer::sum);
        var scores = new HashMap<>(noveltyScores);
        scores.put(hash, novelty);
        return new HabituationState(counts, scores);
    }
}
