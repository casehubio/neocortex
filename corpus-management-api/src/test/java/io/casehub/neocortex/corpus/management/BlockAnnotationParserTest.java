package io.casehub.neocortex.corpus.management;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockAnnotationParserTest {

    private final BlockAnnotationParser parser = new BlockAnnotationParser();

    @Test
    void parseEmptyText() {
        BlockParseResult result = parser.parse("");
        assertThat(result.cleanText()).isEmpty();
        assertThat(result.annotations()).isEmpty();
    }

    @Test
    void parseNullText() {
        BlockParseResult result = parser.parse(null);
        assertThat(result.cleanText()).isEmpty();
        assertThat(result.annotations()).isEmpty();
    }

    @Test
    void parseNoAnnotations() {
        BlockParseResult result = parser.parse("Plain text with no annotations.");
        assertThat(result.cleanText()).isEqualTo("Plain text with no annotations.");
        assertThat(result.annotations()).isEmpty();
    }

    @Test
    void parseSinglePanelAnnotation() {
        String input = "#@ panel: header\n# Title\n\nSome text.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.cleanText()).isEqualTo("# Title\n\nSome text.");
        assertThat(result.annotations()).hasSize(1);
        BlockAnnotation ann = result.annotations().get(0);
        assertThat(ann.type()).isEqualTo("panel");
        assertThat(ann.value()).isEqualTo("header");
        assertThat(ann.startOffset()).isEqualTo(0);
        assertThat(ann.endOffset()).isEqualTo(result.cleanText().length());
    }

    @Test
    void parseMultipleAnnotations() {
        String input = "#@ panel: left\nLeft text.\n#@ panel: right\nRight text.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.cleanText()).isEqualTo("Left text.\nRight text.");
        assertThat(result.annotations()).hasSize(2);
        assertThat(result.annotations().get(0).type()).isEqualTo("panel");
        assertThat(result.annotations().get(0).value()).isEqualTo("left");
        assertThat(result.annotations().get(1).type()).isEqualTo("panel");
        assertThat(result.annotations().get(1).value()).isEqualTo("right");

        String leftText = result.cleanText().substring(
            result.annotations().get(0).startOffset(),
            result.annotations().get(0).endOffset());
        assertThat(leftText).startsWith("Left text.");
    }

    @Test
    void parsePageAnnotation() {
        String input = "#@ page: 1\nPage one.\n#@ page: 2\nPage two.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.annotations()).hasSize(2);
        assertThat(result.annotations().get(0).type()).isEqualTo("page");
        assertThat(result.annotations().get(0).value()).isEqualTo("1");
        assertThat(result.annotations().get(1).type()).isEqualTo("page");
        assertThat(result.annotations().get(1).value()).isEqualTo("2");
    }

    @Test
    void parseAnnotationWithKeyValueParams() {
        String input = "#@ figure: caption=\"Fig 1\" alt=\"diagram\"\nFigure content.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.annotations()).hasSize(1);
        BlockAnnotation ann = result.annotations().get(0);
        assertThat(ann.type()).isEqualTo("figure");
        assertThat(ann.params()).containsEntry("caption", "Fig 1");
        assertThat(ann.params()).containsEntry("alt", "diagram");
    }

    @Test
    void parseAnnotationWithQuotedValue() {
        String input = "#@ ocr: \"Extracted text from image\"\nSurrounding text.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.annotations()).hasSize(1);
        assertThat(result.annotations().get(0).type()).isEqualTo("ocr");
        assertThat(result.annotations().get(0).value()).isEqualTo("Extracted text from image");
    }

    @Test
    void offsetsAreCorrectInCleanText() {
        String input = "#@ panel: a\nAlpha.\n#@ panel: b\nBeta.\n#@ panel: c\nGamma.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.annotations()).hasSize(3);
        for (BlockAnnotation ann : result.annotations()) {
            String segment = result.cleanText().substring(ann.startOffset(), ann.endOffset());
            assertThat(segment).isNotBlank();
        }
        assertThat(result.cleanText().substring(
            result.annotations().get(0).startOffset(),
            result.annotations().get(0).endOffset())).startsWith("Alpha.");
        assertThat(result.cleanText().substring(
            result.annotations().get(2).startOffset(),
            result.annotations().get(2).endOffset()).strip()).isEqualTo("Gamma.");
    }

    @Test
    void annotationLineWithExtraSpaces() {
        String input = "#@   panel:   header  \nContent.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.annotations()).hasSize(1);
        assertThat(result.annotations().get(0).type()).isEqualTo("panel");
        assertThat(result.annotations().get(0).value()).isEqualTo("header");
    }

    @Test
    void nonAnnotationHashLines() {
        String input = "# Heading\n## Subheading\n#not an annotation\nText.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.cleanText()).isEqualTo(input);
        assertThat(result.annotations()).isEmpty();
    }

    @Test
    void layoutAnnotation() {
        String input = "#@ layout: 2-column\n#@ panel: left\nLeft.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.annotations()).hasSize(2);
        assertThat(result.annotations().get(0).type()).isEqualTo("layout");
        assertThat(result.annotations().get(0).value()).isEqualTo("2-column");
        assertThat(result.annotations().get(1).type()).isEqualTo("panel");
        assertThat(result.annotations().get(1).value()).isEqualTo("left");
    }

    @Test
    void describeAnnotation() {
        String input = "#@ describe: \"A bar chart showing growth trends\"\nChart area.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.annotations()).hasSize(1);
        assertThat(result.annotations().get(0).type()).isEqualTo("describe");
        assertThat(result.annotations().get(0).value()).isEqualTo("A bar chart showing growth trends");
    }

    @Test
    void mixedAnnotationsAndContent() {
        String input = "#@ page: 1\n#@ panel: header\n# Title\n\n#@ panel: body\nBody text.\n#@ page: 2\n#@ panel: body\nMore text.";
        BlockParseResult result = parser.parse(input);
        assertThat(result.cleanText()).isEqualTo("# Title\n\nBody text.\nMore text.");
        assertThat(result.annotations()).hasSize(5);
        assertThat(result.annotations().get(0).type()).isEqualTo("page");
        assertThat(result.annotations().get(0).value()).isEqualTo("1");
        assertThat(result.annotations().get(1).type()).isEqualTo("panel");
        assertThat(result.annotations().get(1).value()).isEqualTo("header");
        assertThat(result.annotations().get(2).type()).isEqualTo("panel");
        assertThat(result.annotations().get(2).value()).isEqualTo("body");
        assertThat(result.annotations().get(3).type()).isEqualTo("page");
        assertThat(result.annotations().get(3).value()).isEqualTo("2");
        assertThat(result.annotations().get(4).type()).isEqualTo("panel");
        assertThat(result.annotations().get(4).value()).isEqualTo("body");
    }

    @Test
    void tableAnnotation() {
        String input = "#@ table: rows=3 cols=2\n| A | B |\n| 1 | 2 |";
        BlockParseResult result = parser.parse(input);
        assertThat(result.annotations()).hasSize(1);
        BlockAnnotation ann = result.annotations().get(0);
        assertThat(ann.type()).isEqualTo("table");
        assertThat(ann.params()).containsEntry("rows", "3");
        assertThat(ann.params()).containsEntry("cols", "2");
    }
}
