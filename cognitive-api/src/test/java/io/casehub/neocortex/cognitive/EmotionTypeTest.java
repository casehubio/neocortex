package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionTypeTest {

    @ParameterizedTest
    @EnumSource(EmotionType.class)
    void canonicalNameIsLowercaseHyphenated(EmotionType type) {
        String name = type.canonicalName();
        assertThat(name).matches("[a-z]+(-[a-z]+)*");
    }

    @Test
    void canonicalNameExamples() {
        assertThat(EmotionType.JOY.canonicalName()).isEqualTo("joy");
        assertThat(EmotionType.HAPPY_FOR.canonicalName()).isEqualTo("happy-for");
        assertThat(EmotionType.FEARS_CONFIRMED.canonicalName()).isEqualTo("fears-confirmed");
    }

    @ParameterizedTest
    @EnumSource(EmotionType.class)
    void normaliseReturnsItselfForCanonicalName(EmotionType type) {
        assertThat(EmotionType.normalise(type.canonicalName())).contains(type);
    }

    @ParameterizedTest
    @EnumSource(EmotionType.class)
    void normaliseHandlesEnumName(EmotionType type) {
        assertThat(EmotionType.normalise(type.name())).contains(type);
    }

    @Test
    void normaliseHandlesMixedCase() {
        assertThat(EmotionType.normalise("Joy")).contains(EmotionType.JOY);
        assertThat(EmotionType.normalise("DISTRESS")).contains(EmotionType.DISTRESS);
        assertThat(EmotionType.normalise("Happy-For")).contains(EmotionType.HAPPY_FOR);
    }

    @Test
    void normaliseHandlesNonCanonicalAliases() {
        assertThat(EmotionType.normalise("despair")).contains(EmotionType.DISTRESS);
        assertThat(EmotionType.normalise("frustration")).contains(EmotionType.ANGER);
        assertThat(EmotionType.normalise("grief")).contains(EmotionType.DISTRESS);
        assertThat(EmotionType.normalise("anxiety")).contains(EmotionType.FEAR);
        assertThat(EmotionType.normalise("sadness")).contains(EmotionType.DISTRESS);
        assertThat(EmotionType.normalise("betrayal")).contains(EmotionType.REPROACH);
        assertThat(EmotionType.normalise("regret")).contains(EmotionType.REMORSE);
        assertThat(EmotionType.normalise("sympathy")).contains(EmotionType.PITY);
        assertThat(EmotionType.normalise("satisfaction")).contains(EmotionType.SATISFACTION);
    }

    @Test
    void normaliseMapsDeprecatedSorryForToPity() {
        assertThat(EmotionType.normalise("sorry-for")).contains(EmotionType.PITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "unknown-emotion", "bliss", "euphoria"})
    void normaliseReturnsEmptyForUnknownTypes(String input) {
        assertThat(EmotionType.normalise(input)).isEmpty();
    }

    @Test
    void normaliseReturnsEmptyForNull() {
        assertThat(EmotionType.normalise(null)).isEmpty();
    }

    @Test
    void normaliseTrimsWhitespace() {
        assertThat(EmotionType.normalise("  joy  ")).contains(EmotionType.JOY);
    }
}
