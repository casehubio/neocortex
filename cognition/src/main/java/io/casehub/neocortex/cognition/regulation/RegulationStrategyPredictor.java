package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Personality-driven regulation strategy prediction (Petter et al. 2025).
 * Maps DispositionAxes to strategy preferences, skipping LLM when confidence is high.
 */
public class RegulationStrategyPredictor {

    static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.6;
    // Petter et al. 2025 — high intensity overwhelms cognitive resources
    static final double HIGH_INTENSITY_THRESHOLD = 0.75;
    // Petter et al. 2025 — effectiveness baseline for personality-aligned strategies
    static final double ALIGNED_EFFECTIVENESS = 0.55;
    static final double MISALIGNED_EFFECTIVENESS = 0.35;

    private final double confidenceThreshold;

    public RegulationStrategyPredictor() {
        this(DEFAULT_CONFIDENCE_THRESHOLD);
    }

    public RegulationStrategyPredictor(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public Optional<RegulationPrediction> predict(DispositionAxes axes,
                                                   double peakIntensity,
                                                   double regulationFatigue,
                                                   @Nullable CognitiveLoadLevel cognitiveLoad) {
        // Petter 2025: high fatigue forces low-cost strategy regardless of personality
        if (regulationFatigue > 0.7) {
            return Optional.of(new RegulationPrediction(
                    EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT, 0.85, MISALIGNED_EFFECTIVENESS));
        }

        // Petter 2025: high intensity overwhelms cognitive capacity → distraction
        if (peakIntensity > HIGH_INTENSITY_THRESHOLD) {
            return Optional.of(new RegulationPrediction(
                    EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT, 0.80, ALIGNED_EFFECTIVENESS));
        }

        // Petter 2025: high cognitive load constrains to low-cost strategies
        if (cognitiveLoad == CognitiveLoadLevel.HIGH) {
            var strategy = predictUnderHighLoad(axes);
            return Optional.of(new RegulationPrediction(strategy, 0.75, ALIGNED_EFFECTIVENESS));
        }

        // Personality-driven prediction
        var votes = computeVotes(axes);
        var best = findDominant(votes);
        if (best == null || best.score < confidenceThreshold) {
            return Optional.empty();
        }

        return Optional.of(new RegulationPrediction(
                best.strategy, best.score, ALIGNED_EFFECTIVENESS));
    }

    // Petter 2025 Table 2 — disposition-to-strategy correlations adapted from Big Five
    static Map<EmotionRegulationStrategy, Double> computeVotes(DispositionAxes axes) {
        var votes = new EnumMap<EmotionRegulationStrategy, Double>(EmotionRegulationStrategy.class);
        for (var s : INTERNAL_STRATEGIES) {
            votes.put(s, 0.0);
        }

        // ruleFollowing: strict → conscientiousness → structured coping (COGNITIVE_CHANGE)
        // ruleFollowing: flexible → low conscientiousness → less deliberate (RESPONSE_MODULATION)
        switch (axes.ruleFollowing()) {
            case "strict" -> votes.merge(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.35, Double::sum);
            case "flexible" -> votes.merge(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.2, Double::sum);
        }

        // conflictMode: avoidant → neuroticism/avoidance → suppression (RESPONSE_MODULATION)
        // conflictMode: analytical → openness → reappraisal (COGNITIVE_CHANGE)
        // conflictMode: cooperative → agreeableness → reappraisal (COGNITIVE_CHANGE)
        // conflictMode: competitive → engagement-oriented → controlled expression (RESPONSE_MODULATION)
        switch (axes.conflictMode()) {
            case "avoidant" -> votes.merge(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.35, Double::sum);
            case "analytical" -> votes.merge(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.3, Double::sum);
            case "cooperative" -> votes.merge(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.25, Double::sum);
            case "competitive" -> votes.merge(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.2, Double::sum);
        }

        // socialOrient: cooperative → agreeableness → empathic reappraisal
        // socialOrient: independent → low agreeableness → self-focused suppression
        // socialOrient: competitive → engagement → controlled expression (RESPONSE_MODULATION)
        switch (axes.socialOrient()) {
            case "cooperative" -> votes.merge(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.2, Double::sum);
            case "independent" -> votes.merge(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.15, Double::sum);
            case "competitive" -> votes.merge(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.15, Double::sum);
        }

        // riskAppetite: bold → extraversion → confidence to reframe (COGNITIVE_CHANGE)
        // riskAppetite: conservative → high neuroticism → suppress and contain
        switch (axes.riskAppetite()) {
            case "bold" -> votes.merge(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.25, Double::sum);
            case "conservative" -> votes.merge(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.2, Double::sum);
        }

        // autonomy: high → openness/self-direction → reappraisal
        switch (axes.autonomy()) {
            case "high" -> votes.merge(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.2, Double::sum);
        }

        return votes;
    }

    private static EmotionRegulationStrategy predictUnderHighLoad(DispositionAxes axes) {
        // High load: only low-cost strategies viable (Petter 2025)
        // Suppression (RESPONSE_MODULATION) or distraction (ATTENTIONAL_DEPLOYMENT)
        return switch (axes.conflictMode()) {
            case "avoidant" -> EmotionRegulationStrategy.RESPONSE_MODULATION;
            case "competitive" -> EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT;
            default -> switch (axes.riskAppetite()) {
                case "bold" -> EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT;
                default -> EmotionRegulationStrategy.RESPONSE_MODULATION;
            };
        };
    }

    record StrategyScore(EmotionRegulationStrategy strategy, double score) {}

    static @Nullable StrategyScore findDominant(Map<EmotionRegulationStrategy, Double> votes) {
        EmotionRegulationStrategy best = null;
        double bestScore = 0.0;
        double secondBest = 0.0;

        for (var entry : votes.entrySet()) {
            if (entry.getValue() > bestScore) {
                secondBest = bestScore;
                bestScore = entry.getValue();
                best = entry.getKey();
            } else if (entry.getValue() > secondBest) {
                secondBest = entry.getValue();
            }
        }

        if (best == null || bestScore == 0.0) return null;
        return new StrategyScore(best, bestScore);
    }

    // Environmental strategies need goal directives — never predicted mechanically
    private static final List<EmotionRegulationStrategy> INTERNAL_STRATEGIES = List.of(
            EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT,
            EmotionRegulationStrategy.COGNITIVE_CHANGE,
            EmotionRegulationStrategy.RESPONSE_MODULATION
    );

    static EmotionRegulationResult toResult(RegulationPrediction prediction,
                                             List<String> targetEmotions,
                                             List<String> targetDrives,
                                             double peakOriginalIntensity) {
        double regulated = peakOriginalIntensity * (1.0 - prediction.predictedEffectiveness());
        return new EmotionRegulationResult(
                prediction.strategy(),
                targetEmotions,
                targetDrives,
                peakOriginalIntensity,
                regulated,
                prediction.predictedEffectiveness(),
                narrativeFor(prediction.strategy()),
                null,
                null);
    }

    private static String narrativeFor(EmotionRegulationStrategy strategy) {
        return switch (strategy) {
            case COGNITIVE_CHANGE ->
                    "You reframe the situation, finding a different perspective that softens the emotional impact.";
            case RESPONSE_MODULATION ->
                    "You hold the feeling in check, keeping your expression steady despite the inner turbulence.";
            case ATTENTIONAL_DEPLOYMENT ->
                    "You redirect your focus, turning your attention away from what stirred the feeling.";
            case SITUATION_SELECTION, SITUATION_MODIFICATION ->
                    throw new IllegalArgumentException("Environmental strategies are not mechanically predicted");
        };
    }
}
