package io.casehub.neocortex.cognition.dissonance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class DissonanceTickParticipantTest {

    private static final DissonanceDetectionConfig CONFIG =
        new DissonanceDetectionConfig(true, 0.1, 0.15, 0.3, 3,
            0.15, 0.3, 0.10, 0.2, 0.05, 0.1, 0.5);

    @Test
    void computesArousalIncrease() {
        var tensions = List.of(snapshot(0.8), snapshot(0.6));
        var deltas = DissonanceTickParticipant.computePadDeltas(tensions, CONFIG);
        double expected = Math.min(CONFIG.arousalCap(),
            (0.8 + 0.6) * CONFIG.arousalWeight());
        assertThat(deltas.arousal()).isCloseTo(expected, offset(0.001));
    }

    @Test
    void computesPleasureDecrease() {
        var tensions = List.of(snapshot(0.8));
        var deltas = DissonanceTickParticipant.computePadDeltas(tensions, CONFIG);
        double expected = -Math.min(CONFIG.pleasureCap(),
            0.8 * CONFIG.pleasureWeight());
        assertThat(deltas.pleasure()).isCloseTo(expected, offset(0.001));
    }

    @Test
    void computesDominanceFromMaxTension() {
        var tensions = List.of(snapshot(0.5), snapshot(0.9));
        var deltas = DissonanceTickParticipant.computePadDeltas(tensions, CONFIG);
        double expected = -Math.min(CONFIG.dominanceCap(),
            0.9 * CONFIG.dominanceWeight());
        assertThat(deltas.dominance()).isCloseTo(expected, offset(0.001));
    }

    @Test
    void capsArousalAtConfiguredCap() {
        var tensions = List.of(snapshot(1.0), snapshot(1.0), snapshot(1.0));
        var deltas = DissonanceTickParticipant.computePadDeltas(tensions, CONFIG);
        assertThat(deltas.arousal()).isCloseTo(CONFIG.arousalCap(), offset(0.001));
    }

    @Test
    void returnsZeroDeltasWhenNoTensions() {
        var deltas = DissonanceTickParticipant.computePadDeltas(List.of(), CONFIG);
        assertThat(deltas.arousal()).isCloseTo(0.0, offset(0.001));
        assertThat(deltas.pleasure()).isCloseTo(0.0, offset(0.001));
        assertThat(deltas.dominance()).isCloseTo(0.0, offset(0.001));
    }

    private TensionSnapshot snapshot(double strength) {
        return new TensionSnapshot("n1", "belief-belief",
            "a", "b", "text-a", "text-b", strength, "reason");
    }
}
