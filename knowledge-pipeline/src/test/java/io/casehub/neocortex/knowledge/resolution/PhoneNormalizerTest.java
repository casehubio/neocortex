package io.casehub.neocortex.knowledge.resolution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNormalizerTest {

    @Test
    void normalizesUkInternationalFormat() {
        assertThat(PhoneNormalizer.normalize("+44 131 226 1888"))
            .isEqualTo("01312261888");
    }

    @Test
    void normalizesUkLocalFormat() {
        assertThat(PhoneNormalizer.normalize("0131 226 1888"))
            .isEqualTo("01312261888");
    }

    @Test
    void normalizesUkDoubleZeroFormat() {
        assertThat(PhoneNormalizer.normalize("0044 131 226 1888"))
            .isEqualTo("01312261888");
    }

    @Test
    void stripsSpacesAndDashes() {
        assertThat(PhoneNormalizer.normalize("0131-226-1888"))
            .isEqualTo("01312261888");
    }

    @Test
    void nullReturnsEmpty() {
        assertThat(PhoneNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void blankReturnsEmpty() {
        assertThat(PhoneNormalizer.normalize("  ")).isEmpty();
    }

    @Test
    void internationalAndLocalFormsMatch() {
        assertThat(PhoneNormalizer.normalize("+44 131 226 1888"))
            .isEqualTo(PhoneNormalizer.normalize("0131 226 1888"));
    }
}
