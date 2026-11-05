package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Personality × emotion-type regulation strategy prediction (Petter et al. 2025).
 * Emotion type encodes situation controllability (ANGER = controllable, FEAR = not),
 * so the predictor is situation-aware without reading the scenario text.
 */
public class RegulationStrategyPredictor {

    static final double HIGH_INTENSITY_THRESHOLD = 0.75;
    static final double ALIGNED_EFFECTIVENESS = 0.55;
    static final double MISALIGNED_EFFECTIVENESS = 0.35;

    public RegulationStrategyPredictor() {}

    public Optional<RegulationPrediction> predict(DispositionAxes axes,
                                                   EmotionType dominantEmotion,
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

        // Emotion-type × personality prediction
        return predictFromEmotionAndPersonality(axes, dominantEmotion);
    }

    // Petter 2025 + OCC: emotion type encodes controllability, personality selects within
    Optional<RegulationPrediction> predictFromEmotionAndPersonality(
            DispositionAxes axes, EmotionType emotion) {
        return switch (emotionCategory(emotion)) {
            case CONTROLLABLE_NEGATIVE -> predictForControllableNegative(axes);
            case UNCONTROLLABLE_NEGATIVE -> predictForUncontrollableNegative(axes);
            case SELF_CONSCIOUS -> predictForSelfConscious(axes);
            case OTHER_DIRECTED -> predictForOtherDirected(axes);
            case POSITIVE, UNKNOWN -> Optional.empty();
        };
    }

    // ANGER, HATE, RESENTMENT — agent believes they can act on this
    // Judge findings across 3 runs:
    //   - Reappraiser + CC: 5/5 consistently (optimal)
    //   - Pure suppressor + RM: 3-4/5 (personality-congruent, judge notes CC more adaptive)
    //   - Competitive+avoidant + CC: 2/5 (personality can't execute — low autonomy blocks reframing)
    //   - Competitive+avoidant + RM: 3/5 run 1 (congruent but risks escalation)
    //   - Mild reappraiser + CC: 3/5 (personality lacks levers to power reframing)
    //
    // Rule: only predict what the personality can EXECUTE, not what's theoretically optimal.
    // CC requires reappraisal capacity (3+ signals). RM requires suppression tolerance.
    // Conflicted or underpowered personalities fall through to LLM.
    private Optional<RegulationPrediction> predictForControllableNegative(DispositionAxes axes) {
        if (isReappraiser(axes)) {
            return Optional.of(new RegulationPrediction(
                    EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.70, ALIGNED_EFFECTIVENESS));
        }
        if (isSuppressor(axes) && !isConflictedEngager(axes)) {
            return Optional.of(new RegulationPrediction(
                    EmotionRegulationStrategy.RESPONSE_MODULATION, 0.70, ALIGNED_EFFECTIVENESS));
        }
        return Optional.empty();
    }

    // FEAR, DISTRESS, FEARS_CONFIRMED — agent feels powerless
    // Almost universal → AD (redirect from threat source)
    private Optional<RegulationPrediction> predictForUncontrollableNegative(DispositionAxes axes) {
        return Optional.of(new RegulationPrediction(
                EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT, 0.80, ALIGNED_EFFECTIVENESS));
    }

    // SHAME, REMORSE — self-attribution, failed own standards
    // Judge findings across 4 runs for shame:
    //   - Suppressor + RM: 2/5 (shame festers when suppressed)
    //   - Suppressor + CC: 3/5 (avoidant wouldn't engage with reframing)
    //   - Reappraiser + CC: 5/5 (optimal — analytical engages with meaning)
    //
    // Principle: suppressor personality can't do CC (won't engage) and shouldn't
    // do RM (shame festers). AD is personality-congruent (avoidant redirects)
    // and less harmful than suppression for self-conscious emotions.
    private Optional<RegulationPrediction> predictForSelfConscious(DispositionAxes axes) {
        if (isReappraiser(axes)) {
            return Optional.of(new RegulationPrediction(
                    EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.75, ALIGNED_EFFECTIVENESS));
        }
        if (isSuppressor(axes)) {
            return Optional.of(new RegulationPrediction(
                    EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT, 0.70, ALIGNED_EFFECTIVENESS));
        }
        return Optional.empty();
    }

    // REPROACH, DISAPPOINTMENT, PITY — other failed the agent's standards
    // Avoidant → RM (suppress visible judgment)
    // Analytical → CC (reframe other's behavior)
    private Optional<RegulationPrediction> predictForOtherDirected(DispositionAxes axes) {
        if (isSuppressor(axes)) {
            return Optional.of(new RegulationPrediction(
                    EmotionRegulationStrategy.RESPONSE_MODULATION, 0.70, ALIGNED_EFFECTIVENESS));
        }
        if (isReappraiser(axes)) {
            return Optional.of(new RegulationPrediction(
                    EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.65, ALIGNED_EFFECTIVENESS));
        }
        return Optional.empty();
    }

    // Competitive + avoidant: engagement drive conflicts with avoidance drive.
    // Judge scored RM for this combo at 3/5 — "risks passive-aggressive escalation."
    boolean isConflictedEngager(DispositionAxes axes) {
        boolean competitive = "competitive".equals(axes.conflictMode())
                || "competitive".equals(axes.socialOrient());
        boolean avoidant = "avoidant".equals(axes.conflictMode());
        return competitive && avoidant;
    }

    boolean isSuppressor(DispositionAxes axes) {
        int signals = 0;
        if ("avoidant".equals(axes.conflictMode())) signals += 2;
        if ("conservative".equals(axes.riskAppetite())) signals++;
        if ("independent".equals(axes.socialOrient())) signals++;
        if ("flexible".equals(axes.ruleFollowing())) signals++;
        if ("competitive".equals(axes.conflictMode())) signals++;
        if ("competitive".equals(axes.socialOrient())) signals++;
        return signals >= 3;
    }

    boolean isReappraiser(DispositionAxes axes) {
        return reappraiserSignals(axes) >= 3;
    }

    boolean isMildReappraiser(DispositionAxes axes) {
        return reappraiserSignals(axes) >= 2;
    }

    private int reappraiserSignals(DispositionAxes axes) {
        int signals = 0;
        if ("analytical".equals(axes.conflictMode())) signals += 2;
        if ("strict".equals(axes.ruleFollowing())) signals++;
        if ("cooperative".equals(axes.socialOrient())) signals++;
        if ("high".equals(axes.autonomy())) signals++;
        if ("bold".equals(axes.riskAppetite())) signals++;
        if ("cooperative".equals(axes.conflictMode())) signals++;
        return signals;
    }

    private static EmotionRegulationStrategy predictUnderHighLoad(DispositionAxes axes) {
        return switch (axes.conflictMode()) {
            case "avoidant" -> EmotionRegulationStrategy.RESPONSE_MODULATION;
            case "competitive" -> EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT;
            default -> switch (axes.riskAppetite()) {
                case "bold" -> EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT;
                default -> EmotionRegulationStrategy.RESPONSE_MODULATION;
            };
        };
    }

    enum EmotionCategory {
        CONTROLLABLE_NEGATIVE,
        UNCONTROLLABLE_NEGATIVE,
        SELF_CONSCIOUS,
        OTHER_DIRECTED,
        POSITIVE,
        UNKNOWN
    }

    static EmotionCategory emotionCategory(EmotionType type) {
        return switch (type) {
            case ANGER, HATE, RESENTMENT, GLOATING -> EmotionCategory.CONTROLLABLE_NEGATIVE;
            case FEAR, DISTRESS, FEARS_CONFIRMED -> EmotionCategory.UNCONTROLLABLE_NEGATIVE;
            case SHAME, REMORSE -> EmotionCategory.SELF_CONSCIOUS;
            case REPROACH, DISAPPOINTMENT, PITY -> EmotionCategory.OTHER_DIRECTED;
            case JOY, HOPE, SATISFACTION, RELIEF, PRIDE, ADMIRATION,
                 GRATITUDE, GRATIFICATION, HAPPY_FOR, LOVE -> EmotionCategory.POSITIVE;
        };
    }

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
