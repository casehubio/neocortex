package io.casehub.neocortex.entity.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TokenFormatTest {

    @Test
    void parseStandardToken() {
        var tokens = TokenFormat.parse("Hello {{person_001:full:Jane Doe}} world");
        assertThat(tokens).hasSize(1);
        var t = tokens.get(0);
        assertThat(t.entityId()).isEqualTo("person_001");
        assertThat(t.variant()).isEqualTo("full");
        assertThat(t.surfaceForm()).isEqualTo("Jane Doe");
        assertThat(t.startOffset()).isEqualTo(6);
        assertThat(t.endOffset()).isEqualTo(34);
    }

    @Test
    void parsePronounToken() {
        var tokens = TokenFormat.parse("{{person_001:pronoun:subject:She}} painted");
        assertThat(tokens).hasSize(1);
        var t = tokens.get(0);
        assertThat(t.entityId()).isEqualTo("person_001");
        assertThat(t.variant()).isEqualTo("pronoun:subject");
        assertThat(t.surfaceForm()).isEqualTo("She");
    }

    @Test
    void parseSurfaceFormWithColon() {
        var tokens = TokenFormat.parse("{{person_001:full:Dr. Smith: Surgeon General}}");
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).surfaceForm()).isEqualTo("Dr. Smith: Surgeon General");
    }

    @Test
    void parseMultipleTokens() {
        var text = "{{p1:first:Jane}} met {{p2:first:Diego}}";
        var tokens = TokenFormat.parse(text);
        assertThat(tokens).hasSize(2);
        assertThat(tokens.get(0).surfaceForm()).isEqualTo("Jane");
        assertThat(tokens.get(1).surfaceForm()).isEqualTo("Diego");
    }

    @Test
    void parseNoTokens() {
        assertThat(TokenFormat.parse("plain text")).isEmpty();
        assertThat(TokenFormat.parse("{{not_a_token}}")).isEmpty();
        assertThat(TokenFormat.parse("{{one:colon}}")).isEmpty();
    }

    @Test
    void parseNull() {
        assertThat(TokenFormat.parse(null)).isEmpty();
    }

    @Test
    void emitProducesValidToken() {
        String token = TokenFormat.emit("person_001", "full", "Jane Doe");
        assertThat(token).isEqualTo("{{person_001:full:Jane Doe}}");
    }

    @Test
    void emitPronounToken() {
        String token = TokenFormat.emit("person_001", "pronoun:subject", "She");
        assertThat(token).isEqualTo("{{person_001:pronoun:subject:She}}");
    }

    @Test
    void parseRoundTripsEmit() {
        String original = TokenFormat.emit("p1", "first", "Jane");
        var tokens = TokenFormat.parse("prefix " + original + " suffix");
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).entityId()).isEqualTo("p1");
        assertThat(tokens.get(0).variant()).isEqualTo("first");
        assertThat(tokens.get(0).surfaceForm()).isEqualTo("Jane");
    }

    @Test
    void extractEntityIds() {
        var text = "{{p1:full:Jane}} met {{p2:first:Diego}} and {{p1:last:Doe}}";
        var ids = TokenFormat.extractEntityIds(text);
        assertThat(ids).containsExactlyInAnyOrder("p1", "p2");
    }

    @ParameterizedTest
    @CsvSource({
        "'text with no tokens', 'text with no tokens'",
        "'prefix {{p1:first:Jane}} suffix', 'prefix Jane suffix'"
    })
    void stripTokensReturnsSurfaceForms(String input, String expected) {
        assertThat(TokenFormat.stripTokens(input)).isEqualTo(expected);
    }

    @Test
    void stripTokensMultiple() {
        String input = "{{p1:first:Jane}} met {{p2:first:Diego}}";
        assertThat(TokenFormat.stripTokens(input)).isEqualTo("Jane met Diego");
    }

    @Test
    void stripTokensNull() {
        assertThat(TokenFormat.stripTokens(null)).isNull();
    }
}
