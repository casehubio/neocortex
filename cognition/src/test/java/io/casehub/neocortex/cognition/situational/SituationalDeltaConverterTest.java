package io.casehub.neocortex.cognition.situational;

import io.casehub.eidos.api.AmpdFacetShift;
import io.casehub.eidos.api.SituationalProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SituationalDeltaConverterTest {

    @Test
    void convertsDeltasToUnitScale() {
        var profiles = List.of(new SituationalProfile("stress",
                List.of(new AmpdFacetShift("Anxiousness", 15)), "estimated"));
        var deltas = SituationalDeltaConverter.toSituationalDeltas(profiles);

        assertThat(deltas).containsKey(SituationalMode.STRESS);
        assertThat(deltas.get(SituationalMode.STRESS).get("Anxiousness"))
                .isCloseTo(0.15, within(1e-9));
    }

    @Test
    void negativeDeltaConvertedCorrectly() {
        var profiles = List.of(new SituationalProfile("flow",
                List.of(new AmpdFacetShift("Anxiousness", -10)), "estimated"));
        var deltas = SituationalDeltaConverter.toSituationalDeltas(profiles);

        assertThat(deltas.get(SituationalMode.FLOW).get("Anxiousness"))
                .isCloseTo(-0.10, within(1e-9));
    }

    @Test
    void allFiveModesConverted() {
        var profiles = SituationalProfile.VALID_MODES.stream()
                .map(mode -> new SituationalProfile(mode,
                        List.of(new AmpdFacetShift("Hostility", 5)), "test"))
                .toList();
        var deltas = SituationalDeltaConverter.toSituationalDeltas(profiles);

        assertThat(deltas).hasSize(5);
        assertThat(deltas).containsKeys(SituationalMode.values());
    }

    @Test
    void emptyProfilesGiveEmptyDeltas() {
        assertThat(SituationalDeltaConverter.toSituationalDeltas(List.of())).isEmpty();
        assertThat(SituationalDeltaConverter.toSituationalDeltas(null)).isEmpty();
    }

    @Test
    void multipleShiftsPerMode() {
        var profiles = List.of(new SituationalProfile("stress",
                List.of(new AmpdFacetShift("Anxiousness", 15),
                        new AmpdFacetShift("Hostility", 10),
                        new AmpdFacetShift("Withdrawal", -8)),
                "estimated"));
        var deltas = SituationalDeltaConverter.toSituationalDeltas(profiles);

        var stressDeltas = deltas.get(SituationalMode.STRESS);
        assertThat(stressDeltas).hasSize(3);
        assertThat(stressDeltas.get("Anxiousness")).isCloseTo(0.15, within(1e-9));
        assertThat(stressDeltas.get("Hostility")).isCloseTo(0.10, within(1e-9));
        assertThat(stressDeltas.get("Withdrawal")).isCloseTo(-0.08, within(1e-9));
    }

    @Test
    void unknownModeIgnored() {
        var deltas = SituationalDeltaConverter.toMode("nonexistent");
        assertThat(deltas).isNull();
    }

    @Test
    void modeConversionCaseInsensitive() {
        assertThat(SituationalDeltaConverter.toMode("STRESS")).isEqualTo(SituationalMode.STRESS);
        assertThat(SituationalDeltaConverter.toMode("stress")).isEqualTo(SituationalMode.STRESS);
        assertThat(SituationalDeltaConverter.toMode("social_collaborative"))
                .isEqualTo(SituationalMode.SOCIAL_COLLABORATIVE);
    }
}
