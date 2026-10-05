package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.AlmaPadTable;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EmotionMapper {

    private EmotionMapper() {}

    public static List<CognitiveEmotion> mapEmotions(List<SecResult> results, String subjectId) {
        var dims = mergeDimensions(results);
        var emotions = new ArrayList<CognitiveEmotion>();
        var now = Instant.now();

        double relevance = dims.getOrDefault(SecDimensions.RELEVANCE, 0.0);
        double conduciveness = dims.getOrDefault(SecDimensions.CONDUCIVENESS, 0.0);
        double controllability = dims.getOrDefault(SecDimensions.CONTROLLABILITY, 0.5);
        double internalStd = dims.getOrDefault(SecDimensions.INTERNAL_STANDARDS, 1.0);
        double externalStd = dims.getOrDefault(SecDimensions.EXTERNAL_STANDARDS, 1.0);

        if (relevance < 0.2) return emotions;

        if (conduciveness < -0.2 && controllability < 0.4) {
            double intensity = clampIntensity(relevance * Math.abs(conduciveness) * (1.0 - controllability));
            emotions.add(emotion(EmotionType.FEAR, intensity, subjectId, now));
        } else if (conduciveness < -0.2 && controllability > 0.5) {
            double intensity = clampIntensity(relevance * Math.abs(conduciveness) * controllability);
            emotions.add(emotion(EmotionType.ANGER, intensity, subjectId, now));
        } else if (conduciveness < -0.2) {
            double intensity = clampIntensity(relevance * Math.abs(conduciveness));
            emotions.add(emotion(EmotionType.DISTRESS, intensity, subjectId, now));
        }

        if (conduciveness > 0.3) {
            double intensity = clampIntensity(relevance * conduciveness);
            emotions.add(emotion(EmotionType.JOY, intensity, subjectId, now));
        }

        if (internalStd < 0.5) {
            double intensity = clampIntensity(relevance * (1.0 - internalStd));
            emotions.add(emotion(EmotionType.SHAME, intensity, subjectId, now));
        }

        if (externalStd < 0.5) {
            double intensity = clampIntensity(relevance * (1.0 - externalStd));
            emotions.add(emotion(EmotionType.REPROACH, intensity, subjectId, now));
        }

        return emotions;
    }

    public static List<ActionTendency> mapTendencies(List<SecResult> results) {
        var dims = mergeDimensions(results);
        var tendencies = new ArrayList<ActionTendency>();

        double conduciveness = dims.getOrDefault(SecDimensions.CONDUCIVENESS, 0.0);
        double controllability = dims.getOrDefault(SecDimensions.CONTROLLABILITY, 0.5);
        double novelty = dims.getOrDefault(SecDimensions.NOVELTY, 0.0);

        if (conduciveness > 0.3) {
            tendencies.add(new ActionTendency(ActionReadiness.APPROACH, clampIntensity(conduciveness), ""));
        } else if (conduciveness < -0.3 && controllability < 0.4) {
            tendencies.add(new ActionTendency(ActionReadiness.AVOIDANCE, clampIntensity(Math.abs(conduciveness)), ""));
        } else if (conduciveness < -0.3 && controllability > 0.5) {
            tendencies.add(new ActionTendency(ActionReadiness.ANTAGONISM, clampIntensity(Math.abs(conduciveness)), ""));
        }

        if (novelty > 0.5) {
            tendencies.add(new ActionTendency(ActionReadiness.ATTENDING, clampIntensity(novelty), ""));
        } else if (novelty < 0.2) {
            tendencies.add(new ActionTendency(ActionReadiness.INTERRUPTION, clampIntensity(1.0 - novelty), ""));
        }

        return tendencies;
    }

    static Map<String, Double> mergeDimensions(List<SecResult> results) {
        var merged = new HashMap<String, Double>();
        for (var result : results) {
            merged.putAll(result.dimensions());
        }
        return merged;
    }

    private static CognitiveEmotion emotion(EmotionType type, double intensity,
                                            String subjectId, Instant onset) {
        return new CognitiveEmotion(type, intensity, subjectId, onset,
                EmotionSource.INTRINSIC, AlmaPadTable.project(type, intensity));
    }

    private static double clampIntensity(double value) {
        return Math.max(0.01, Math.min(1.0, value));
    }
}
