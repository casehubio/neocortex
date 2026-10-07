package io.casehub.neocortex.memory.experience;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SubThoughtTypesTest {

    @Test
    void knownTypesAreRecognised() {
        assertThat(SubThoughtTypes.isKnown("affect-observation")).isTrue();
        assertThat(SubThoughtTypes.isKnown("causal-inference")).isTrue();
        assertThat(SubThoughtTypes.isKnown("evaluative")).isTrue();
        assertThat(SubThoughtTypes.isKnown("intention")).isTrue();
        assertThat(SubThoughtTypes.isKnown("self-reflection")).isTrue();
        assertThat(SubThoughtTypes.isKnown("association")).isTrue();
        assertThat(SubThoughtTypes.isKnown("concern")).isTrue();
    }

    @Test
    void unknownTypeIsNotRecognised() {
        assertThat(SubThoughtTypes.isKnown("unknown-type")).isFalse();
        assertThat(SubThoughtTypes.isKnown("")).isFalse();
    }

    @Test
    void validateThrowsForUnknownType() {
        assertThatThrownBy(() -> SubThoughtTypes.validate("bogus"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown sub-thought type: bogus");
    }

    @Test
    void validateAcceptsKnownType() {
        assertThatCode(() -> SubThoughtTypes.validate("intention"))
            .doesNotThrowAnyException();
    }

    @Test
    void constantsMatchExpectedValues() {
        assertThat(SubThoughtTypes.AFFECT_OBSERVATION).isEqualTo("affect-observation");
        assertThat(SubThoughtTypes.CAUSAL_INFERENCE).isEqualTo("causal-inference");
        assertThat(SubThoughtTypes.EVALUATIVE).isEqualTo("evaluative");
        assertThat(SubThoughtTypes.INTENTION).isEqualTo("intention");
        assertThat(SubThoughtTypes.SELF_REFLECTION).isEqualTo("self-reflection");
        assertThat(SubThoughtTypes.ASSOCIATION).isEqualTo("association");
        assertThat(SubThoughtTypes.CONCERN).isEqualTo("concern");
    }
}
