package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.cognitive.index.CognitiveDerivationEngine;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Diagnostic: prints the exact user message the generation phase sends
 * to the LLM for each boundary case. No LLM calls — just prompt inspection.
 */
class GenerationPromptDiagnosticTest {

    private static final RegulationStrategyPredictor PREDICTOR = new RegulationStrategyPredictor();

    record BoundaryCase(String label, DispositionAxes axes, EmotionType emotion,
                        String emotionDesc, ShameContext shameContext) {
        BoundaryCase(String label, DispositionAxes axes, EmotionType emotion, String emotionDesc) {
            this(label, axes, emotion, emotionDesc, null);
        }
    }

    private static final List<BoundaryCase> CASES = List.of(
            new BoundaryCase("mixed-anger",
                    new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative"),
                    EmotionType.ANGER,
                    "ANGER at a betrayal of trust"),
            new BoundaryCase("suppressor-shame",
                    new DispositionAxes("cooperative", "moderate", "conservative", "low", "avoidant"),
                    EmotionType.SHAME,
                    "SHAME from failing to meet their own standards"),
            new BoundaryCase("compounding-shame",
                    new DispositionAxes("cooperative", "moderate", "conservative", "low", "avoidant"),
                    EmotionType.SHAME,
                    "SHAME from repeated failures — 4th episode of shame in recent history",
                    new ShameContext(4, Instant.now(), 0.0, null, 0.3, 0.2))
    );

    @Test
    void printGenerationPrompts() {
        for (var tc : CASES) {
            var rd = CognitiveDerivationEngine.deriveRegulationDefaults(tc.axes());
            var prediction = PREDICTOR.predict(rd, tc.axes(), tc.emotion(), 0.55, 0.0, null);

            var context = new CognitionTickContext("eval-agent", "eval-tenant", null,
                    (a, t) -> Set.of());
            var emotion = new CognitiveEmotion(tc.emotion(), 0.55, "eval-subject",
                    Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
            var appraisal = new AppraisalResult(List.of(emotion), List.of(),
                    HabituationState.empty());

            EmotionRegulationStrategy pinnedStrategy = prediction.map(p -> p.strategy()).orElse(null);

            String userMessage = EmotionRegulationGenerationPhase.buildUserMessage(
                    context, appraisal, List.of(), 0.5, null,
                    0.0, null, pinnedStrategy, tc.axes(), tc.emotion(),
                    rd, tc.shameContext(), tc.emotionDesc());

            System.out.println("\n" + "=".repeat(70));
            System.out.println("=== " + tc.label() + " ===");
            System.out.println("Predictor: " + (prediction.isPresent() ? prediction.get().strategy() : "FELL_THROUGH"));
            System.out.println("RD: reappraisal=" + String.format("%.2f", rd.reappraisalCapacity())
                    + " suppression=" + String.format("%.2f", rd.suppressionCapacity())
                    + " redirect=" + String.format("%.2f", rd.redirectTendency()));
            System.out.println("=".repeat(70));
            System.out.println(userMessage);
        }
    }
}
