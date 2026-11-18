package io.casehub.neocortex.cognitive.observability;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record PredictionCalibrationResult(
        List<SourceSummary> sourceSummaries,
        int predictorEpisodes,
        int llmEpisodes,
        int totalEpisodes,
        double predictorMeanEffectiveness,
        double llmMeanEffectiveness,
        double delta,
        boolean miscalibrated,
        boolean inconclusive,
        @Nullable String inconclusiveReason) {

    public record SourceSummary(String source, int episodeCount, double meanEffectiveness) {}
}
