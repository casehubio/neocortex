package io.casehub.neocortex.cognitive;

import java.time.Duration;
import java.util.Map;

public record RecurringThoughtConfig(
        Map<String, Double> symptomWeights,
        double fixationThreshold,
        double obsessionThreshold,
        double ruminationThreshold,
        Duration recurrenceWindow,
        int minObservations) {

    public RecurringThoughtConfig {
        if (symptomWeights == null) symptomWeights = Map.of();
        else symptomWeights = Map.copyOf(symptomWeights);
    }

    public static RecurringThoughtConfig defaults() {
        return new RecurringThoughtConfig(
                Map.of(),
                0.3, 0.6, 0.8,
                Duration.ofDays(7),
                3);
    }
}
