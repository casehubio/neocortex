package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.mood.MoodBaseline;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionDefaultsTest {

    @Test
    void empty_allFieldsNull() {
        var empty = EmotionDefaults.empty();
        assertThat(empty.moodBaseline()).isNull();
        assertThat(empty.appraisalWeights()).isNull();
        assertThat(empty.regulationDefaults()).isNull();
        assertThat(empty.copingDefaults()).isNull();
    }

    @Test
    void mergeWith_explicitWins() {
        var explicitMood = new MoodBaseline(0.5, 0.3, 0.1);
        var derivedMood = new MoodBaseline(-0.2, 0.0, 0.4);
        var derivedRegulation = new RegulationDefaults(0.6, 0.4, 0.3, 0.5);

        var explicit = new EmotionDefaults(explicitMood, null, null, null);
        var derived = new EmotionDefaults(derivedMood, null, derivedRegulation, null);

        var merged = explicit.mergeWith(derived);
        assertThat(merged.moodBaseline()).isSameAs(explicitMood);
        assertThat(merged.regulationDefaults()).isSameAs(derivedRegulation);
    }

    @Test
    void mergeWith_bothNull_returnsNull() {
        var merged = EmotionDefaults.empty().mergeWith(EmotionDefaults.empty());
        assertThat(merged.moodBaseline()).isNull();
        assertThat(merged.regulationDefaults()).isNull();
    }

    @Test
    void mergeWith_derivedNull_returnsThis() {
        var explicitMood = new MoodBaseline(0.5, 0.3, 0.1);
        var explicit = new EmotionDefaults(explicitMood, null, null, null);

        var merged = explicit.mergeWith(null);
        assertThat(merged).isSameAs(explicit);
    }
}
