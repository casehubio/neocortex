package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.regulation.EmotionRegulationMindMapAdapter.SourceOutcomeSummary;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PredictionCalibrationAnalyzerTest {

    @Test
    void detectsMiscalibrationWhenLlmOutperformsPredictor() {
        var summaries = Map.of(
                "PREDICTOR", new SourceOutcomeSummary("PREDICTOR", 25, 0.55),
                "LLM", new SourceOutcomeSummary("LLM", 30, 0.75)
        );

        var report = PredictionCalibrationAnalyzer.analyze(summaries, 20);

        assertThat(report.miscalibrated()).isTrue();
        assertThat(report.predictorMeanEffectiveness()).isCloseTo(0.55, within(0.001));
        assertThat(report.llmMeanEffectiveness()).isCloseTo(0.75, within(0.001));
        assertThat(report.delta()).isCloseTo(-0.20, within(0.001));
        assertThat(report.predictorEpisodes()).isEqualTo(25);
        assertThat(report.llmEpisodes()).isEqualTo(30);
        assertThat(report.totalEpisodes()).isEqualTo(55);
        assertThat(report.sourceSummaries()).hasSize(2);
    }

    @Test
    void notMiscalibratedWhenDeltaWithinMargin() {
        var summaries = Map.of(
                "PREDICTOR", new SourceOutcomeSummary("PREDICTOR", 25, 0.70),
                "LLM", new SourceOutcomeSummary("LLM", 30, 0.75)
        );

        var report = PredictionCalibrationAnalyzer.analyze(summaries, 20);

        assertThat(report.miscalibrated()).isFalse();
        assertThat(report.delta()).isCloseTo(-0.05, within(0.001));
    }

    @Test
    void predictorOutperformingLlmIsNotMiscalibrated() {
        var summaries = Map.of(
                "PREDICTOR", new SourceOutcomeSummary("PREDICTOR", 25, 0.80),
                "LLM", new SourceOutcomeSummary("LLM", 30, 0.65)
        );

        var report = PredictionCalibrationAnalyzer.analyze(summaries, 20);

        assertThat(report.miscalibrated()).isFalse();
        assertThat(report.delta()).isCloseTo(0.15, within(0.001));
    }

    @Test
    void insufficientPredictorEpisodesReportsInconclusive() {
        var summaries = Map.of(
                "PREDICTOR", new SourceOutcomeSummary("PREDICTOR", 5, 0.40),
                "LLM", new SourceOutcomeSummary("LLM", 30, 0.80)
        );

        var report = PredictionCalibrationAnalyzer.analyze(summaries, 20);

        assertThat(report.miscalibrated()).isFalse();
        assertThat(report.inconclusive()).isTrue();
        assertThat(report.inconclusiveReason()).contains("PREDICTOR");
    }

    @Test
    void insufficientLlmEpisodesReportsInconclusive() {
        var summaries = Map.of(
                "PREDICTOR", new SourceOutcomeSummary("PREDICTOR", 25, 0.55),
                "LLM", new SourceOutcomeSummary("LLM", 8, 0.80)
        );

        var report = PredictionCalibrationAnalyzer.analyze(summaries, 20);

        assertThat(report.miscalibrated()).isFalse();
        assertThat(report.inconclusive()).isTrue();
        assertThat(report.inconclusiveReason()).contains("LLM");
    }

    @Test
    void noPredictorEpisodesReportsInconclusive() {
        var summaries = Map.of(
                "LLM", new SourceOutcomeSummary("LLM", 30, 0.75),
                "SAFETY_GATE", new SourceOutcomeSummary("SAFETY_GATE", 10, 0.60)
        );

        var report = PredictionCalibrationAnalyzer.analyze(summaries, 20);

        assertThat(report.miscalibrated()).isFalse();
        assertThat(report.inconclusive()).isTrue();
        assertThat(report.predictorEpisodes()).isEqualTo(0);
    }

    @Test
    void noLlmEpisodesReportsInconclusive() {
        var summaries = Map.of(
                "PREDICTOR", new SourceOutcomeSummary("PREDICTOR", 25, 0.70),
                "CAPS", new SourceOutcomeSummary("CAPS", 15, 0.65)
        );

        var report = PredictionCalibrationAnalyzer.analyze(summaries, 20);

        assertThat(report.miscalibrated()).isFalse();
        assertThat(report.inconclusive()).isTrue();
        assertThat(report.llmEpisodes()).isEqualTo(0);
    }

    @Test
    void includesAllSourceSummariesInReport() {
        var summaries = Map.of(
                "PREDICTOR", new SourceOutcomeSummary("PREDICTOR", 25, 0.60),
                "LLM", new SourceOutcomeSummary("LLM", 30, 0.70),
                "SAFETY_GATE", new SourceOutcomeSummary("SAFETY_GATE", 10, 0.50),
                "CAPS", new SourceOutcomeSummary("CAPS", 8, 0.65)
        );

        var report = PredictionCalibrationAnalyzer.analyze(summaries, 20);

        assertThat(report.sourceSummaries()).hasSize(4);
        assertThat(report.totalEpisodes()).isEqualTo(73);
    }

    @Test
    void emptyInputReportsInconclusive() {
        var report = PredictionCalibrationAnalyzer.analyze(Map.of(), 20);

        assertThat(report.miscalibrated()).isFalse();
        assertThat(report.inconclusive()).isTrue();
        assertThat(report.totalEpisodes()).isEqualTo(0);
    }

    @Test
    void customMiscalibrationMargin() {
        var summaries = Map.of(
                "PREDICTOR", new SourceOutcomeSummary("PREDICTOR", 25, 0.65),
                "LLM", new SourceOutcomeSummary("LLM", 30, 0.75)
        );

        var reportDefault = PredictionCalibrationAnalyzer.analyze(summaries, 20);
        assertThat(reportDefault.miscalibrated()).isFalse();

        var reportTight = PredictionCalibrationAnalyzer.analyze(summaries, 20, 0.05);
        assertThat(reportTight.miscalibrated()).isTrue();
    }
}
