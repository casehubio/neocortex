package io.casehub.neocortex.annotation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnchorAnnotationParserTest {

    private final AnchorAnnotationParser parser = new AnchorAnnotationParser();

    @Test
    void parseSingleAnchor() {
        AnchorParseResult result = parser.parse("James[pp] is protective.");
        assertThat(result.cleanText()).isEqualTo("James is protective.");
        assertThat(result.anchors()).hasSize(1);
        assertThat(result.anchors().get(0).alias()).isEqualTo("pp");
        assertThat(result.anchors().get(0).surfaceForm()).isEqualTo("James");
        assertThat(result.anchors().get(0).startOffset()).isEqualTo(0);
        assertThat(result.anchors().get(0).endOffset()).isEqualTo(5);
        assertThat(result.cleanText().substring(0, 5)).isEqualTo("James");
    }

    @Test
    void parseMultipleAnchors() {
        AnchorParseResult result = parser.parse("He[pp] examined it[obj-7] carefully.");
        assertThat(result.cleanText()).isEqualTo("He examined it carefully.");
        assertThat(result.anchors()).hasSize(2);
        assertThat(result.anchors().get(0).alias()).isEqualTo("pp");
        assertThat(result.anchors().get(0).surfaceForm()).isEqualTo("He");
        assertThat(result.anchors().get(1).alias()).isEqualTo("obj-7");
        assertThat(result.anchors().get(1).surfaceForm()).isEqualTo("it");
    }

    @Test
    void singleWordBeforeBracket() {
        AnchorParseResult result = parser.parse("the guest book[obj-7] was old.");
        assertThat(result.cleanText()).isEqualTo("the guest book was old.");
        assertThat(result.anchors()).hasSize(1);
        assertThat(result.anchors().get(0).surfaceForm()).isEqualTo("book");
        assertThat(result.anchors().get(0).alias()).isEqualTo("obj-7");
    }

    @Test
    void noAnchors() {
        AnchorParseResult result = parser.parse("Plain text with no anchors.");
        assertThat(result.cleanText()).isEqualTo("Plain text with no anchors.");
        assertThat(result.anchors()).isEmpty();
    }

    @Test
    void emptyText() {
        AnchorParseResult result = parser.parse("");
        assertThat(result.cleanText()).isEmpty();
        assertThat(result.anchors()).isEmpty();
    }

    @Test
    void offsetsAreCorrectInCleanText() {
        AnchorParseResult result = parser.parse("James[pp] gave Clara[clara] the key[key1].");
        assertThat(result.cleanText()).isEqualTo("James gave Clara the key.");
        for (ParsedAnchor a : result.anchors()) {
            assertThat(result.cleanText().substring(a.startOffset(), a.endOffset()))
                .isEqualTo(a.surfaceForm());
        }
    }

    @Test
    void adjacentAnchors() {
        AnchorParseResult result = parser.parse("James[pp] Clara[clara] met.");
        assertThat(result.cleanText()).isEqualTo("James Clara met.");
        assertThat(result.anchors()).hasSize(2);
        assertThat(result.anchors().get(0).surfaceForm()).isEqualTo("James");
        assertThat(result.anchors().get(1).surfaceForm()).isEqualTo("Clara");
    }
}
