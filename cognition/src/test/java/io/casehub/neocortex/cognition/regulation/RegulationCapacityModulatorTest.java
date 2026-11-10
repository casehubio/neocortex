package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.index.RegulationDefaults;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RegulationCapacityModulatorTest {

    private static final double TOLERANCE = 0.001;
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Test
    void noOutcomes_returnsBaselineUnchanged() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var result = RegulationCapacityModulator.modulate(baseline, List.of(), NOW);
        assertThat(result).isEqualTo(baseline);
    }

    @Test
    void nullOutcomes_returnsBaselineUnchanged() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var result = RegulationCapacityModulator.modulate(baseline, null, NOW);
        assertThat(result).isEqualTo(baseline);
    }

    @Test
    void successfulOutcomes_noDegrade() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var outcomes = List.of(
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.6),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.8));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes, NOW);
        assertThat(result.reappraisalCapacity()).isCloseTo(0.70, within(TOLERANCE));
    }

    @Test
    void recentFailure_fullDegradation() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var outcomes = List.of(recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.2));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes, NOW);
        // Recent (weight ≈ 1.0): penalty ≈ 0.12; modulator ≈ 0.88; effective ≈ 0.616
        assertThat(result.reappraisalCapacity()).isCloseTo(0.616, within(TOLERANCE));
        assertThat(result.suppressionCapacity()).isCloseTo(0.65, within(TOLERANCE));
    }

    @Test
    void fourRecentSSFailures_redirectDropsBelowPinThreshold() {
        var baseline = new RegulationDefaults(0.40, 0.65, 0.65, 0.50);
        var outcomes = List.of(
                recentOutcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.15),
                recentOutcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.20),
                recentOutcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.10),
                recentOutcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.25));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes, NOW);
        assertThat(result.redirectTendency()).isLessThan(RegulationStrategyPredictor.VIABLE_ALTERNATIVE);
    }

    @Test
    void oldFailures_minimalDegradation() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.65, 0.50);
        // 30 days ago: weight = exp(-0.099 * 30) ≈ 0.052; penalty ≈ 0.12 * 0.052 ≈ 0.006
        var outcomes = List.of(
                outcomeAt(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1, NOW.minus(Duration.ofDays(30))),
                outcomeAt(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1, NOW.minus(Duration.ofDays(30))),
                outcomeAt(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1, NOW.minus(Duration.ofDays(30))),
                outcomeAt(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1, NOW.minus(Duration.ofDays(30))));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes, NOW);
        // Total penalty ≈ 4 * 0.006 ≈ 0.025; redirect ≈ 0.65 * 0.975 ≈ 0.634
        assertThat(result.redirectTendency()).isGreaterThan(0.60);
    }

    @Test
    void recentVsOld_recencyWeightsMore() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var recent = RegulationCapacityModulator.modulate(baseline,
                List.of(recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1)), NOW);
        var old = RegulationCapacityModulator.modulate(baseline,
                List.of(outcomeAt(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1, NOW.minus(Duration.ofDays(14)))), NOW);
        assertThat(recent.reappraisalCapacity()).isLessThan(old.reappraisalCapacity());
    }

    @Test
    void mixedSSandAD_bothCountAsRedirect() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.70, 0.50);
        var outcomes = List.of(
                recentOutcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.2),
                recentOutcome(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT, 0.1));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes, NOW);
        assertThat(result.redirectTendency()).isLessThan(0.70);
        assertThat(result.redirectTendency()).isGreaterThan(0.70 * 0.20);
    }

    @Test
    void floorPreventsFullDepletion() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var outcomes = List.of(
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes, NOW);
        assertThat(result.reappraisalCapacity()).isCloseTo(0.70 * 0.20, within(TOLERANCE));
    }

    @Test
    void socialRegulationTendency_neverDegraded() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.80);
        var outcomes = List.of(
                recentOutcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                recentOutcome(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.1),
                recentOutcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes, NOW);
        assertThat(result.socialRegulationTendency()).isCloseTo(0.80, within(TOLERANCE));
    }

    @Test
    void strategySpecific_onlyDegradeMatchingCapacity() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var outcomes = List.of(
                recentOutcome(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.1),
                recentOutcome(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.2));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes, NOW);
        assertThat(result.reappraisalCapacity()).isCloseTo(0.70, within(TOLERANCE));
        assertThat(result.redirectTendency()).isCloseTo(0.60, within(TOLERANCE));
        assertThat(result.suppressionCapacity()).isLessThan(0.65);
    }

    @Test
    void recoveryOverTime_capacityApproachesBaseline() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.65, 0.50);
        var failures = List.of(
                outcomeAt(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1, NOW.minus(Duration.ofDays(60))),
                outcomeAt(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1, NOW.minus(Duration.ofDays(60))),
                outcomeAt(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1, NOW.minus(Duration.ofDays(60))),
                outcomeAt(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1, NOW.minus(Duration.ofDays(60))));
        var result = RegulationCapacityModulator.modulate(baseline, failures, NOW);
        // 60 days ago: weight ≈ 0.0025; total penalty ≈ 0.001; effectively recovered
        assertThat(result.redirectTendency()).isGreaterThan(0.64);
    }

    private static RegulationOutcome recentOutcome(EmotionRegulationStrategy strategy,
                                                    double effectiveness) {
        return outcomeAt(strategy, effectiveness, NOW.minus(Duration.ofMinutes(30)));
    }

    private static RegulationOutcome outcomeAt(EmotionRegulationStrategy strategy,
                                                double effectiveness, Instant timestamp) {
        return new RegulationOutcome(strategy, effectiveness, timestamp);
    }
}
