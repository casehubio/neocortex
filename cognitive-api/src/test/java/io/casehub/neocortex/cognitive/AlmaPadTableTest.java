package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

class AlmaPadTableTest {

    @Test
    void fearHasCorrectBaseValues() {
        var pad = AlmaPadTable.lookup(EmotionType.FEAR);
        assertThat(pad.pleasure()).isCloseTo(-0.64, within(0.01));
        assertThat(pad.arousal()).isCloseTo(0.60, within(0.01));
        assertThat(pad.dominance()).isCloseTo(-0.43, within(0.01));
    }

    @Test
    void hopeHasCorrectBaseValues() {
        var pad = AlmaPadTable.lookup(EmotionType.HOPE);
        assertThat(pad.pleasure()).isCloseTo(0.2, within(0.01));
        assertThat(pad.arousal()).isCloseTo(0.2, within(0.01));
        assertThat(pad.dominance()).isCloseTo(-0.1, within(0.01));
    }

    @Test
    void satisfactionHasCorrectBaseValues() {
        var pad = AlmaPadTable.lookup(EmotionType.SATISFACTION);
        assertThat(pad.pleasure()).isCloseTo(0.3, within(0.01));
        assertThat(pad.arousal()).isCloseTo(-0.2, within(0.01));
        assertThat(pad.dominance()).isCloseTo(0.4, within(0.01));
    }

    @Test
    void projectScalesByIntensity() {
        var pad = AlmaPadTable.project(EmotionType.FEAR, 0.5);
        assertThat(pad.pleasure()).isCloseTo(-0.32, within(0.01));
        assertThat(pad.arousal()).isCloseTo(0.30, within(0.01));
        assertThat(pad.dominance()).isCloseTo(-0.215, within(0.01));
    }

    @Test
    void projectAtZeroIntensityReturnsZeroPad() {
        var pad = AlmaPadTable.project(EmotionType.FEAR, 0.0);
        assertThat(pad.pleasure()).isEqualTo(0.0);
        assertThat(pad.arousal()).isEqualTo(0.0);
        assertThat(pad.dominance()).isEqualTo(0.0);
    }

    @Test
    void projectAtFullIntensityReturnsBaseValues() {
        var base = AlmaPadTable.lookup(EmotionType.JOY);
        var projected = AlmaPadTable.project(EmotionType.JOY, 1.0);
        assertThat(projected.pleasure()).isEqualTo(base.pleasure());
        assertThat(projected.arousal()).isEqualTo(base.arousal());
        assertThat(projected.dominance()).isEqualTo(base.dominance());
    }

    @ParameterizedTest
    @EnumSource(EmotionType.class)
    void allEmotionTypesHaveMappings(EmotionType type) {
        assertThatCode(() -> AlmaPadTable.lookup(type)).doesNotThrowAnyException();
    }
}
