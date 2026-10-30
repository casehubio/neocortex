package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.BehavioralAttractor;
import io.casehub.neocortex.caps.ConvergenceType;
import io.casehub.neocortex.caps.SettlingResult;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationOutcomeEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

    @Test
    void convergedWithStrongAttractor_highSuccessRate() {
        var result = new SettlingResult(Map.of(), 5, ConvergenceType.CONVERGED, 0.1,
            List.of(new BehavioralAttractor("approach", "reward", 0.8, false, 1)));

        CbrOutcome outcome = ClassificationOutcomeEvaluator.evaluate(result, NOW);

        assertThat(outcome.successRate()).isEqualTo(0.8);
        assertThat(outcome.result()).isEqualTo(CbrOutcome.Outcome.PARTIAL);
    }

    @Test
    void convergedWithNoAttractors_zeroSuccessRate() {
        var result = new SettlingResult(Map.of(), 5, ConvergenceType.CONVERGED, 0.1,
            List.of());

        CbrOutcome outcome = ClassificationOutcomeEvaluator.evaluate(result, NOW);

        assertThat(outcome.successRate()).isEqualTo(0.0);
        assertThat(outcome.result()).isEqualTo(CbrOutcome.Outcome.FAILURE);
    }

    @Test
    void oscillation_lowSuccessRate() {
        var result = new SettlingResult(Map.of(), 50, ConvergenceType.OSCILLATION, 0.5,
            List.of(new BehavioralAttractor("approach", "reward", 0.8, false, 1)));

        CbrOutcome outcome = ClassificationOutcomeEvaluator.evaluate(result, NOW);

        assertThat(outcome.successRate()).isCloseTo(0.24, org.assertj.core.data.Offset.offset(0.001));
        assertThat(outcome.result()).isEqualTo(CbrOutcome.Outcome.PARTIAL);
    }

    @Test
    void maxIterations_veryLowSuccessRate() {
        var result = new SettlingResult(Map.of(), 100, ConvergenceType.MAX_ITERATIONS, 0.9,
            List.of(new BehavioralAttractor("approach", "reward", 0.7, false, 1)));

        CbrOutcome outcome = ClassificationOutcomeEvaluator.evaluate(result, NOW);

        assertThat(outcome.successRate()).isCloseTo(0.07, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void multipleAttractors_usesMaxStrength() {
        var result = new SettlingResult(Map.of(), 5, ConvergenceType.CONVERGED, 0.1,
            List.of(
                new BehavioralAttractor("approach", "reward", 0.3, false, 1),
                new BehavioralAttractor("avoidance", "threat", 0.9, false, 1)));

        CbrOutcome outcome = ClassificationOutcomeEvaluator.evaluate(result, NOW);

        assertThat(outcome.successRate()).isEqualTo(0.9);
    }

    @Test
    void perfectConvergenceAndAttractor_fullSuccess() {
        var result = new SettlingResult(Map.of(), 3, ConvergenceType.CONVERGED, 0.0,
            List.of(new BehavioralAttractor("approach", "reward", 1.0, false, 1)));

        CbrOutcome outcome = ClassificationOutcomeEvaluator.evaluate(result, NOW);

        assertThat(outcome.successRate()).isEqualTo(1.0);
        assertThat(outcome.result()).isEqualTo(CbrOutcome.Outcome.SUCCESS);
    }

    @Test
    void detailContainsConvergenceAndAttractorInfo() {
        var result = new SettlingResult(Map.of(), 5, ConvergenceType.CONVERGED, 0.1,
            List.of(new BehavioralAttractor("approach", "reward", 0.75, false, 1)));

        CbrOutcome outcome = ClassificationOutcomeEvaluator.evaluate(result, NOW);

        assertThat(outcome.detail()).contains("CONVERGED");
        assertThat(outcome.detail()).contains("1 attractors");
        assertThat(outcome.detail()).contains("0.75");
    }
}
