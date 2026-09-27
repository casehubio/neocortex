package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.AlmaPadTable;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.mindmap.AppraisalContext;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HeuristicGoalAppraisal implements GoalAppraisal {

    @Override
    public List<CognitiveEmotion> appraise(MindMapNode goal, AppraisalContext context) {
        String status = goal.property("status").orElse("active");
        double priority = doubleProperty(goal, "priority", 0.5);
        double urgency = doubleProperty(goal, "urgency", 0.0);
        double feasibility = doubleProperty(goal, "feasibility", 0.5);
        int surfacingCount = context.surfacingCount();
        Instant now = Instant.now();

        var emotions = new ArrayList<CognitiveEmotion>();

        switch (status) {
            case "active" -> appraiseActive(goal, context, emotions,
                    priority, urgency, feasibility, surfacingCount, now);
            case "blocked" -> appraiseBlocked(goal, context, emotions,
                    priority, urgency, feasibility, now);
            case "completed" -> appraiseCompleted(goal, emotions, priority, now);
            case "abandoned" -> appraiseAbandoned(goal, emotions, priority, now);
            case "dormant" -> appraiseDormant(goal, emotions, priority, now);
            default -> {}
        }

        appraiseEmpathic(goal, context, emotions, urgency, feasibility, now);

        return List.copyOf(emotions);
    }

    private void appraiseActive(MindMapNode goal, AppraisalContext context,
                                 List<CognitiveEmotion> emotions,
                                 double priority, double urgency, double feasibility,
                                 int surfacingCount, Instant now) {
        double surfacingGapFactor = surfacingCount / (surfacingCount + 1.0);
        double uw = context.weights().urgencyWeight();

        double hopeIntensity = clampIntensity(priority * feasibility * (1 - urgency * 0.5 * uw));
        if (hopeIntensity > 0.05) {
            emotions.add(emotion(EmotionType.HOPE, hopeIntensity, goal.id(), now));
        }

        double threshold = 0.3 * context.weights().fearOnsetThreshold();
        if (urgency > threshold || surfacingCount > 0) {
            double fearIntensity = clampIntensity(
                    priority * urgency * uw * Math.max(surfacingGapFactor, 0.3));
            if (fearIntensity > 0.05) {
                emotions.add(emotion(EmotionType.FEAR, fearIntensity, goal.id(), now));
            }
        }
    }

    private void appraiseBlocked(MindMapNode goal, AppraisalContext context,
                                  List<CognitiveEmotion> emotions,
                                  double priority, double urgency, double feasibility,
                                  Instant now) {
        double uw = context.weights().urgencyWeight();
        double distressIntensity = clampIntensity(
                priority * urgency * uw * (1 - feasibility));
        if (distressIntensity > 0.05) {
            emotions.add(emotion(EmotionType.DISTRESS, distressIntensity, goal.id(), now));
        }
    }

    private void appraiseCompleted(MindMapNode goal, List<CognitiveEmotion> emotions,
                                    double priority, Instant now) {
        double satisfactionIntensity = clampIntensity(priority);
        emotions.add(emotion(EmotionType.SATISFACTION, satisfactionIntensity, goal.id(), now));
    }

    private void appraiseAbandoned(MindMapNode goal, List<CognitiveEmotion> emotions,
                                    double priority, Instant now) {
        double disappointmentIntensity = clampIntensity(priority * 0.7);
        emotions.add(emotion(EmotionType.DISAPPOINTMENT, disappointmentIntensity, goal.id(), now));
    }

    private void appraiseDormant(MindMapNode goal, List<CognitiveEmotion> emotions,
                                  double priority, Instant now) {
        double distressIntensity = clampIntensity(priority * 0.15);
        if (distressIntensity > 0.05) {
            emotions.add(emotion(EmotionType.DISTRESS, distressIntensity, goal.id(), now));
        }
    }

    private void appraiseEmpathic(MindMapNode goal, AppraisalContext context,
                                   List<CognitiveEmotion> emotions,
                                   double urgency, double feasibility, Instant now) {
        Optional<String> affectedEntity = goal.property("affected-entity");
        if (affectedEntity.isEmpty()) return;

        Double relationshipScore = context.relationshipScores().get(affectedEntity.get());
        if (relationshipScore == null || relationshipScore <= 0) return;

        double negativeProspect = urgency * (1 - feasibility);
        double rw = context.weights().relationshipWeight();
        double pityIntensity = clampIntensity(relationshipScore * rw * negativeProspect);
        if (pityIntensity > 0.1) {
            emotions.add(new CognitiveEmotion(
                    EmotionType.PITY, pityIntensity, affectedEntity.get(), now,
                    EmotionSource.EMPATHIC, AlmaPadTable.project(EmotionType.PITY, pityIntensity)));
        }
    }

    private static CognitiveEmotion emotion(EmotionType type, double intensity,
                                             String subjectId, Instant now) {
        return new CognitiveEmotion(type, intensity, subjectId, now,
                EmotionSource.INTRINSIC, AlmaPadTable.project(type, intensity));
    }

    private static double clampIntensity(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }

    private static double doubleProperty(MindMapNode node, String key, double defaultValue) {
        return node.property(key)
                .map(v -> {
                    try { return Double.parseDouble(v); }
                    catch (NumberFormatException e) { return defaultValue; }
                })
                .orElse(defaultValue);
    }
}
