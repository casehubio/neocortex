package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.index.RegulationDefaults;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RegulationCapacityModulatorTest {

    private static final double TOLERANCE = 0.001;

    @Test
    void noOutcomes_returnsBaselineUnchanged() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var result = RegulationCapacityModulator.modulate(baseline, List.of());
        assertThat(result).isEqualTo(baseline);
    }

    @Test
    void nullOutcomes_returnsBaselineUnchanged() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var result = RegulationCapacityModulator.modulate(baseline, null);
        assertThat(result).isEqualTo(baseline);
    }

    @Test
    void successfulOutcomes_noDegrade() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var outcomes = List.of(
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.6),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.8));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes);
        assertThat(result.reappraisalCapacity()).isCloseTo(0.70, within(TOLERANCE));
    }

    @Test
    void singleFailure_degradesByOneFactor() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var outcomes = List.of(
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.2));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes);
        // modulator = 1.0 - 0.12 = 0.88; effective = 0.70 * 0.88 = 0.616
        assertThat(result.reappraisalCapacity()).isCloseTo(0.616, within(TOLERANCE));
        assertThat(result.suppressionCapacity()).isCloseTo(0.65, within(TOLERANCE));
    }

    @Test
    void fourSSFailures_redirectDropsBelowPinThreshold() {
        var baseline = new RegulationDefaults(0.40, 0.65, 0.65, 0.50);
        var outcomes = List.of(
                outcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.15),
                outcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.20),
                outcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.10),
                outcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.25));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes);
        // modulator = max(0.20, 1.0 - 0.12*4) = max(0.20, 0.52) = 0.52
        // effective redirect = 0.65 * 0.52 = 0.338
        assertThat(result.redirectTendency()).isCloseTo(0.338, within(TOLERANCE));
        assertThat(result.redirectTendency()).isLessThan(RegulationStrategyPredictor.VIABLE_ALTERNATIVE);
    }

    @Test
    void mixedSSandAD_bothCountAsRedirect() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.70, 0.50);
        var outcomes = List.of(
                outcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.2),
                outcome(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT, 0.1));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes);
        // 2 redirect failures: modulator = 1.0 - 0.24 = 0.76
        assertThat(result.redirectTendency()).isCloseTo(0.70 * 0.76, within(TOLERANCE));
    }

    @Test
    void floorPreventsFullDepletion() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var outcomes = List.of(
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes);
        // 10 failures: modulator = max(0.20, 1.0 - 1.2) = 0.20
        assertThat(result.reappraisalCapacity()).isCloseTo(0.70 * 0.20, within(TOLERANCE));
    }

    @Test
    void socialRegulationTendency_neverDegraded() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.80);
        var outcomes = List.of(
                outcome(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.1),
                outcome(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.1),
                outcome(EmotionRegulationStrategy.SITUATION_SELECTION, 0.1));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes);
        assertThat(result.socialRegulationTendency()).isCloseTo(0.80, within(TOLERANCE));
    }

    @Test
    void strategySpecific_onlyDegradeMatchingCapacity() {
        var baseline = new RegulationDefaults(0.70, 0.65, 0.60, 0.50);
        var outcomes = List.of(
                outcome(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.1),
                outcome(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.2));
        var result = RegulationCapacityModulator.modulate(baseline, outcomes);
        assertThat(result.reappraisalCapacity()).isCloseTo(0.70, within(TOLERANCE));
        assertThat(result.redirectTendency()).isCloseTo(0.60, within(TOLERANCE));
        // 2 RM failures: modulator = 1.0 - 0.24 = 0.76
        assertThat(result.suppressionCapacity()).isCloseTo(0.65 * 0.76, within(TOLERANCE));
    }

    private static EmotionRegulationResult outcome(EmotionRegulationStrategy strategy,
                                                    double effectiveness) {
        return new EmotionRegulationResult(strategy, List.of("SHAME"), List.of(),
                0.6, 0.6 * (1 - effectiveness), effectiveness, "test", null, null);
    }
}
