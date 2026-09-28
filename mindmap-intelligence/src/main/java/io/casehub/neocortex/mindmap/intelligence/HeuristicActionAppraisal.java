package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.AlmaPadTable;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.mindmap.ActionAppraisal;
import io.casehub.neocortex.mindmap.ActionContext;
import io.casehub.neocortex.mindmap.ActionOutcome;

import java.util.ArrayList;
import java.util.List;

public class HeuristicActionAppraisal implements ActionAppraisal {

    private static final double BASE_THRESHOLD = 0.2;
    private static final double COMPOUND_RELEVANCE_THRESHOLD = 0.3;

    @Override
    public List<CognitiveEmotion> appraise(ActionContext context) {
        double polarity = switch (context.outcome()) {
            case SUCCESS -> 1.0;
            case FAILURE -> -1.0;
            case NEUTRAL -> 0.0;
        };

        double praiseworthiness = polarity * Math.abs(context.goalRelevance());
        if (praiseworthiness == 0.0) return List.of();

        double strictness = context.isSelfAction()
                ? context.weights().selfStandardsStrictness()
                : context.weights().otherStandardsStrictness();

        var emotions = new ArrayList<CognitiveEmotion>();

        if (praiseworthiness > 0) {
            double positiveThreshold = BASE_THRESHOLD * strictness;
            if (praiseworthiness > positiveThreshold) {
                double intensity = clampIntensity(praiseworthiness / strictness);
                EmotionType type = context.isSelfAction() ? EmotionType.PRIDE : EmotionType.ADMIRATION;
                EmotionSource source = context.isSelfAction() ? EmotionSource.INTRINSIC : EmotionSource.ATTRIBUTED;
                emotions.add(emotion(type, intensity, context, source));
                addCompoundIfEligible(emotions, type, context, intensity);
            }
        } else {
            double negativeThreshold = BASE_THRESHOLD / strictness;
            if (Math.abs(praiseworthiness) > negativeThreshold) {
                double intensity = clampIntensity(Math.abs(praiseworthiness) * strictness);
                EmotionType type = context.isSelfAction() ? EmotionType.SHAME : EmotionType.REPROACH;
                EmotionSource source = context.isSelfAction() ? EmotionSource.INTRINSIC : EmotionSource.ATTRIBUTED;
                emotions.add(emotion(type, intensity, context, source));
                addCompoundIfEligible(emotions, type, context, intensity);
            }
        }

        return List.copyOf(emotions);
    }

    private void addCompoundIfEligible(List<CognitiveEmotion> emotions,
                                        EmotionType baseType, ActionContext context,
                                        double baseIntensity) {
        double absRelevance = Math.abs(context.goalRelevance());
        if (absRelevance <= COMPOUND_RELEVANCE_THRESHOLD) return;

        EmotionType compoundType = switch (baseType) {
            case PRIDE -> context.outcome() == ActionOutcome.SUCCESS ? EmotionType.GRATIFICATION : null;
            case SHAME -> context.outcome() == ActionOutcome.FAILURE ? EmotionType.REMORSE : null;
            case ADMIRATION -> context.goalRelevance() > COMPOUND_RELEVANCE_THRESHOLD ? EmotionType.GRATITUDE : null;
            case REPROACH -> context.goalRelevance() < -COMPOUND_RELEVANCE_THRESHOLD ? EmotionType.ANGER : null;
            default -> null;
        };

        if (compoundType != null) {
            double compoundIntensity = clampIntensity(Math.max(baseIntensity, absRelevance));
            EmotionSource source = context.isSelfAction() ? EmotionSource.INTRINSIC : EmotionSource.ATTRIBUTED;
            emotions.add(emotion(compoundType, compoundIntensity, context, source));
        }
    }

    private static CognitiveEmotion emotion(EmotionType type, double intensity,
                                             ActionContext context, EmotionSource source) {
        return new CognitiveEmotion(type, intensity, context.actingAgentId(),
                context.timestamp(), source, AlmaPadTable.project(type, intensity));
    }

    private static double clampIntensity(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }
}
