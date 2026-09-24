package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitiveEmotionTest {

    @Test
    void validEmotionCreatesSuccessfully() {
        var emotion = new CognitiveEmotion(
                EmotionType.FEAR, 0.8, "goal-1", Instant.now(),
                EmotionSource.INTRINSIC, AlmaPadTable.project(EmotionType.FEAR, 0.8));
        assertThat(emotion.type()).isEqualTo(EmotionType.FEAR);
        assertThat(emotion.intensity()).isEqualTo(0.8);
        assertThat(emotion.source()).isEqualTo(EmotionSource.INTRINSIC);
        assertThat(emotion.pad().pleasure()).isLessThan(0);
    }

    @Test
    void intensityAboveOneRejected() {
        assertThatThrownBy(() -> new CognitiveEmotion(
                EmotionType.FEAR, 1.5, "goal-1", Instant.now(),
                EmotionSource.INTRINSIC, PadProjection.NEUTRAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intensity");
    }

    @Test
    void intensityBelowZeroRejected() {
        assertThatThrownBy(() -> new CognitiveEmotion(
                EmotionType.FEAR, -0.1, "goal-1", Instant.now(),
                EmotionSource.INTRINSIC, PadProjection.NEUTRAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intensity");
    }

    @Test
    void nullSubjectIdRejected() {
        assertThatThrownBy(() -> new CognitiveEmotion(
                EmotionType.FEAR, 0.5, null, Instant.now(),
                EmotionSource.INTRINSIC, PadProjection.NEUTRAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTypeRejected() {
        assertThatThrownBy(() -> new CognitiveEmotion(
                null, 0.5, "goal-1", Instant.now(),
                EmotionSource.INTRINSIC, PadProjection.NEUTRAL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void boundaryIntensitiesAccepted() {
        var atZero = new CognitiveEmotion(
                EmotionType.HOPE, 0.0, "goal-1", Instant.now(),
                EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
        assertThat(atZero.intensity()).isEqualTo(0.0);

        var atOne = new CognitiveEmotion(
                EmotionType.HOPE, 1.0, "goal-1", Instant.now(),
                EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
        assertThat(atOne.intensity()).isEqualTo(1.0);
    }

    @Test
    void empathicSourceDistinctFromIntrinsic() {
        var empathic = new CognitiveEmotion(
                EmotionType.PITY, 0.6, "daughter-node", Instant.now(),
                EmotionSource.EMPATHIC, AlmaPadTable.project(EmotionType.PITY, 0.6));
        assertThat(empathic.source()).isEqualTo(EmotionSource.EMPATHIC);
    }
}
