package io.casehub.neocortex.cognition.regulation;

import java.util.Map;

public record CalibrationReport(
        Map<String, SourceOutcomeSummary> sourceSummaries,
        int predictorEpisodes,
        int llmEpisodes,
        int totalEpisodes,
        double predictorMeanEffectiveness,
        double llmMeanEffectiveness,
        double delta,
        boolean miscalibrated,
        boolean inconclusive,
        @org.jspecify.annotations.Nullable String inconclusiveReason) {
}
