package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.index.RegulationDefaults;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bonanno &amp; Burton 2013: regulatory flexibility — adaptive adjustment based on feedback.
 * Degrades effective regulatory capacity when prior outcomes show repeated strategy failure,
 * weighted by recency (exponential decay, half-life 7 days).
 */
public class RegulationCapacityModulator {

    static final double FAILURE_EFFECTIVENESS_THRESHOLD = 0.4;
    static final double DEGRADATION_PER_FAILURE = 0.12;
    static final double MODULATOR_FLOOR = 0.20;
    // Exponential decay half-life: 7 days → λ = ln(2)/7 ≈ 0.099
    static final double DECAY_LAMBDA = Math.log(2) / 7.0;

    private RegulationCapacityModulator() {}

    public static RegulationDefaults modulate(RegulationDefaults baseline,
                                               List<RegulationOutcome> emotionOutcomes) {
        return modulate(baseline, emotionOutcomes, Instant.now());
    }

    public static RegulationDefaults modulate(RegulationDefaults baseline,
                                               List<RegulationOutcome> emotionOutcomes,
                                               Instant now) {
        if (emotionOutcomes == null || emotionOutcomes.isEmpty()) {
            return baseline;
        }

        double ccPenalty = weightedPenalty(emotionOutcomes, EmotionRegulationStrategy.COGNITIVE_CHANGE, now);
        double rmPenalty = weightedPenalty(emotionOutcomes, EmotionRegulationStrategy.RESPONSE_MODULATION, now);
        double redirectPenalty = weightedPenalty(emotionOutcomes, EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT, now)
                              + weightedPenalty(emotionOutcomes, EmotionRegulationStrategy.SITUATION_SELECTION, now);

        return new RegulationDefaults(
                degrade(baseline.reappraisalCapacity(), ccPenalty),
                degrade(baseline.suppressionCapacity(), rmPenalty),
                degrade(baseline.redirectTendency(), redirectPenalty),
                baseline.socialRegulationTendency());
    }

    static double degrade(double baseline, double totalPenalty) {
        if (totalPenalty <= 0.0) return baseline;
        double modulator = Math.max(MODULATOR_FLOOR, 1.0 - totalPenalty);
        return baseline * modulator;
    }

    static double weightedPenalty(List<RegulationOutcome> outcomes,
                                   EmotionRegulationStrategy strategy,
                                   Instant now) {
        double total = 0.0;
        for (var o : outcomes) {
            if (o.strategy() != strategy) continue;
            if (o.effectiveness() >= FAILURE_EFFECTIVENESS_THRESHOLD) continue;
            double daysSince = Duration.between(o.timestamp(), now).toHours() / 24.0;
            double weight = Math.exp(-DECAY_LAMBDA * Math.max(0, daysSince));
            total += DEGRADATION_PER_FAILURE * weight;
        }
        return total;
    }
}
