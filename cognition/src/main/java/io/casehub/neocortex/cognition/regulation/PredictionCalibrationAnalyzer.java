package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.regulation.EmotionRegulationMindMapAdapter.SourceOutcomeSummary;

import java.util.Map;

public final class PredictionCalibrationAnalyzer {

    static final double DEFAULT_MISCALIBRATION_MARGIN = 0.15;

    private PredictionCalibrationAnalyzer() {}

    public static CalibrationReport analyze(Map<String, SourceOutcomeSummary> summaries, int minEpisodes) {
        return analyze(summaries, minEpisodes, DEFAULT_MISCALIBRATION_MARGIN);
    }

    public static CalibrationReport analyze(Map<String, SourceOutcomeSummary> summaries,
                                            int minEpisodes, double miscalibrationMargin) {
        var predictor = summaries.get("PREDICTOR");
        var llm = summaries.get("LLM");

        int predictorEpisodes = predictor != null ? predictor.episodeCount() : 0;
        int llmEpisodes = llm != null ? llm.episodeCount() : 0;
        int totalEpisodes = summaries.values().stream().mapToInt(SourceOutcomeSummary::episodeCount).sum();

        double predictorMean = predictor != null ? predictor.meanEffectiveness() : 0.0;
        double llmMean = llm != null ? llm.meanEffectiveness() : 0.0;
        double delta = predictorMean - llmMean;

        if (predictorEpisodes < minEpisodes || llmEpisodes < minEpisodes) {
            String reason;
            if (predictorEpisodes == 0 && llmEpisodes == 0) {
                reason = "No PREDICTOR or LLM episodes";
            } else if (predictorEpisodes < minEpisodes && llmEpisodes < minEpisodes) {
                reason = "Insufficient PREDICTOR (%d) and LLM (%d) episodes (min %d)"
                        .formatted(predictorEpisodes, llmEpisodes, minEpisodes);
            } else if (predictorEpisodes < minEpisodes) {
                reason = "Insufficient PREDICTOR episodes (%d, min %d)"
                        .formatted(predictorEpisodes, minEpisodes);
            } else {
                reason = "Insufficient LLM episodes (%d, min %d)"
                        .formatted(llmEpisodes, minEpisodes);
            }
            return new CalibrationReport(summaries, predictorEpisodes, llmEpisodes, totalEpisodes,
                    predictorMean, llmMean, delta, false, true, reason);
        }

        boolean miscalibrated = delta < -miscalibrationMargin;

        return new CalibrationReport(summaries, predictorEpisodes, llmEpisodes, totalEpisodes,
                predictorMean, llmMean, delta, miscalibrated, false, null);
    }
}
