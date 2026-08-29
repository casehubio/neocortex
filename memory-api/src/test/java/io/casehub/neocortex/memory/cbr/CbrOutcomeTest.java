package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

class CbrOutcomeTest {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");

    @Test
    void of_successRate1_isSuccess() {
        CbrOutcome outcome = CbrOutcome.of(1.0, "all passed", NOW);
        assertThat(outcome.result()).isEqualTo(CbrOutcome.Outcome.SUCCESS);
        assertThat(outcome.successRate()).isEqualTo(1.0);
        assertThat(outcome.detail()).isEqualTo("all passed");
        assertThat(outcome.observedAt()).isEqualTo(NOW);
    }

    @Test
    void of_successRate0_isFailure() {
        CbrOutcome outcome = CbrOutcome.of(0.0, null, NOW);
        assertThat(outcome.result()).isEqualTo(CbrOutcome.Outcome.FAILURE);
    }

    @Test
    void of_successRateBetween_isPartial() {
        CbrOutcome outcome = CbrOutcome.of(0.75, "3 of 4", NOW);
        assertThat(outcome.result()).isEqualTo(CbrOutcome.Outcome.PARTIAL);
    }

    @Test
    void constructor_rejectsNegativeRate() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CbrOutcome(CbrOutcome.Outcome.FAILURE, -0.1, null, NOW));
    }

    @Test
    void constructor_rejectsRateAboveOne() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CbrOutcome(CbrOutcome.Outcome.SUCCESS, 1.1, null, NOW));
    }

    @Test
    void constructor_rejectsNullResult() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CbrOutcome(null, 0.5, null, NOW));
    }

    @Test
    void constructor_rejectsNullObservedAt() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CbrOutcome(CbrOutcome.Outcome.SUCCESS, 1.0, null, null));
    }

    @Test
    void adjustConfidence_emaFormula() {
        Confidence result = CbrOutcome.adjustConfidence(Confidence.unknown(0.8), 1.0, 0.2);
        assertThat(result.value()).isCloseTo(0.84, within(0.001));
        assertThat(result.origin()).isEqualTo(ConfidenceOrigin.UNKNOWN);
    }

    @Test
    void adjustConfidence_failure_decreases() {
        Confidence result = CbrOutcome.adjustConfidence(Confidence.unknown(0.8), 0.0, 0.2);
        assertThat(result.value()).isCloseTo(0.64, within(0.001));
    }

    @Test
    void adjustConfidence_partial() {
        Confidence result = CbrOutcome.adjustConfidence(Confidence.unknown(0.8), 0.5, 0.2);
        assertThat(result.value()).isCloseTo(0.74, within(0.001));
    }

    @Test
    void adjustConfidence_nullOldConfidence_treatsAsOne() {
        Confidence result = CbrOutcome.adjustConfidence(null, 0.0, 0.2);
        assertThat(result.value()).isCloseTo(0.8, within(0.001));
        assertThat(result.origin()).isEqualTo(ConfidenceOrigin.UNKNOWN);
    }

    @Test
    void adjustConfidence_convergesToObservedRate() {
        Confidence confidence = Confidence.unknown(0.5);
        for (int i = 0; i < 50; i++) {
            confidence = CbrOutcome.adjustConfidence(confidence, 1.0, 0.2);
        }
        assertThat(confidence.value()).isCloseTo(1.0, within(0.01));
    }

    @Test
    void adjustConfidence_preservesOrigin() {
        Confidence stated = Confidence.stated(0.8, Instant.parse("2026-07-13T10:00:00Z"));
        Confidence result = CbrOutcome.adjustConfidence(stated, 1.0, 0.2);
        assertThat(result.origin()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(result.value()).isCloseTo(0.84, within(0.001));
        assertThat(result.decayReference()).isNull();
    }


    @Test
    void defaultLearningRate() {
        assertThat(CbrOutcome.DEFAULT_LEARNING_RATE).isEqualTo(0.2);
    }
}
