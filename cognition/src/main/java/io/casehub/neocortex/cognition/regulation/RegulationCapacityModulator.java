package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.index.RegulationDefaults;

import java.util.List;

/**
 * Bonanno &amp; Burton 2013: regulatory flexibility — adaptive adjustment based on feedback.
 * Degrades effective regulatory capacity when prior outcomes show repeated strategy failure.
 */
public class RegulationCapacityModulator {

    static final double FAILURE_EFFECTIVENESS_THRESHOLD = 0.4;
    static final double DEGRADATION_PER_FAILURE = 0.12;
    static final double MODULATOR_FLOOR = 0.20;

    private RegulationCapacityModulator() {}

    public static RegulationDefaults modulate(RegulationDefaults baseline,
                                               List<EmotionRegulationResult> emotionOutcomes) {
        if (emotionOutcomes == null || emotionOutcomes.isEmpty()) {
            return baseline;
        }

        int ccFailures = countFailures(emotionOutcomes, EmotionRegulationStrategy.COGNITIVE_CHANGE);
        int rmFailures = countFailures(emotionOutcomes, EmotionRegulationStrategy.RESPONSE_MODULATION);
        int redirectFailures = countFailures(emotionOutcomes, EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT)
                             + countFailures(emotionOutcomes, EmotionRegulationStrategy.SITUATION_SELECTION);

        return new RegulationDefaults(
                degrade(baseline.reappraisalCapacity(), ccFailures),
                degrade(baseline.suppressionCapacity(), rmFailures),
                degrade(baseline.redirectTendency(), redirectFailures),
                baseline.socialRegulationTendency());
    }

    static double degrade(double baseline, int failureCount) {
        if (failureCount == 0) return baseline;
        double modulator = Math.max(MODULATOR_FLOOR, 1.0 - DEGRADATION_PER_FAILURE * failureCount);
        return baseline * modulator;
    }

    static int countFailures(List<EmotionRegulationResult> outcomes,
                              EmotionRegulationStrategy strategy) {
        return (int) outcomes.stream()
                .filter(o -> o.strategy() == strategy)
                .filter(o -> o.effectiveness() < FAILURE_EFFECTIVENESS_THRESHOLD)
                .count();
    }
}
