package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.BehavioralAttractor;
import io.casehub.neocortex.caps.SettlingResult;
import io.casehub.neocortex.memory.cbr.CbrOutcome;

import java.time.Instant;

public final class ClassificationOutcomeEvaluator {

    private ClassificationOutcomeEvaluator() {}

    public static CbrOutcome evaluate(SettlingResult result, Instant now) {
        double convergenceFactor = switch (result.convergence()) {
            case CONVERGED      -> 1.0;
            case OSCILLATION    -> 0.3;
            case MAX_ITERATIONS -> 0.1;
        };
        double attractorFactor = result.attractors().stream()
            .mapToDouble(BehavioralAttractor::strength)
            .max().orElse(0.0);
        double successRate = convergenceFactor * attractorFactor;

        String detail = String.format("%s, %d attractors, max-strength=%.2f",
            result.convergence(), result.attractors().size(), attractorFactor);

        return CbrOutcome.of(successRate, detail, now);
    }
}
