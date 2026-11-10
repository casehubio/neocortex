package io.casehub.neocortex.cognition.regulation;

import java.time.Instant;

public record RegulationOutcome(
        EmotionRegulationStrategy strategy,
        double effectiveness,
        Instant timestamp
) {
    public static RegulationOutcome of(EmotionRegulationResult result, Instant timestamp) {
        return new RegulationOutcome(result.strategy(), result.effectiveness(), timestamp);
    }
}
