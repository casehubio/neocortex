package io.casehub.neocortex.entity.token.cdi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenisingBiographyPreprocessorTest {

    @Test
    void stripsBracketSyntax() {
        assertThat(TokenisingBiographyPreprocessor.stripBrackets("Frida[frida-k] painted"))
            .isEqualTo("Frida painted");
    }

    @Test
    void stripsMultipleBrackets() {
        assertThat(TokenisingBiographyPreprocessor.stripBrackets("Frida[frida-k] met Diego[diego-r]"))
            .isEqualTo("Frida met Diego");
    }

    @Test
    void preservesTextWithoutBrackets() {
        assertThat(TokenisingBiographyPreprocessor.stripBrackets("plain text"))
            .isEqualTo("plain text");
    }

    @Test
    void handlesNullText() {
        assertThat(TokenisingBiographyPreprocessor.stripBrackets(null)).isNull();
    }

    @Test
    void preservesSquareBracketsInNonAnchorContext() {
        assertThat(TokenisingBiographyPreprocessor.stripBrackets("list [a, b, c] here"))
            .isEqualTo("list [a, b, c] here");
    }
}
