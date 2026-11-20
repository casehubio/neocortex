package io.casehub.neocortex.cognition.eval;

import io.casehub.neocortex.cognition.appraisal.ActionReadiness;
import io.casehub.neocortex.cognition.appraisal.ActionTendency;
import io.casehub.neocortex.cognition.appraisal.AppraisalContext;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.AppraisalStrategy;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.appraisal.SecDimensions;
import io.casehub.neocortex.cognitive.AlmaPadTable;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.mindmap.AppraisalWeights;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TestAppraisalStrategy implements AppraisalStrategy {

    @Override
    public AppraisalResult appraise(AppraisalContext context) {
        var weights = context.weights();
        var axes = context.dispositionAxes();

        var secDimensions = computeSecDimensions(weights);
        var tendencies = deriveTendencies(axes);
        var emotions = deriveEmotions(secDimensions);

        return new AppraisalResult(emotions, tendencies,
                HabituationState.empty(), null, secDimensions);
    }

    private static Map<String, Double> computeSecDimensions(AppraisalWeights weights) {
        if (weights == null) {
            return Map.of(
                    SecDimensions.RELEVANCE, 0.8,
                    SecDimensions.NOVELTY, 0.5,
                    SecDimensions.CONDUCIVENESS, 0.0,
                    SecDimensions.CONTROLLABILITY, 0.5);
        }
        double conduciveness = (weights.fearOnsetThreshold() - 1.0) * 0.5;
        double controllability = Math.clamp(1.0 - (weights.urgencyWeight() - 1.0) * 0.3, 0.0, 1.0);

        return Map.of(
                SecDimensions.RELEVANCE, 0.8,
                SecDimensions.NOVELTY, 0.5,
                SecDimensions.CONDUCIVENESS, conduciveness,
                SecDimensions.CONTROLLABILITY, controllability);
    }

    private static List<ActionTendency> deriveTendencies(DispositionAxes axes) {
        if (axes == null) {
            return List.of(new ActionTendency(ActionReadiness.ATTENDING, 0.4, ""));
        }
        return switch (axes.conflictMode()) {
            case "cooperative" -> List.of(new ActionTendency(ActionReadiness.APPROACH, 0.6, ""));
            case "avoidant" -> List.of(new ActionTendency(ActionReadiness.AVOIDANCE, 0.6, ""));
            default -> List.of(new ActionTendency(ActionReadiness.ATTENDING, 0.4, ""));
        };
    }

    private static List<CognitiveEmotion> deriveEmotions(Map<String, Double> sec) {
        double conduciveness = sec.getOrDefault(SecDimensions.CONDUCIVENESS, 0.0);
        var type = conduciveness >= 0 ? EmotionType.INTEREST : EmotionType.FEAR;
        double intensity = Math.clamp(Math.abs(conduciveness) + 0.3, 0.1, 0.9);
        var pad = AlmaPadTable.project(type, intensity);
        return List.of(new CognitiveEmotion(type, intensity, "appraisal",
                Instant.now(), EmotionSource.INTRINSIC, pad));
    }
}
