package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.PanelDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionalMarkdownBuilderTest {

    private final PositionalMarkdownBuilder builder = new PositionalMarkdownBuilder();

    @Test
    void buildsSingleColumnDocument() {
        var layout = new Layout("single",
            List.of(new PanelDefinition("main", 0.0, 0.0, 1.0, 1.0)),
            1, new int[]{612, 792});
        var parseResult = new ParseResult(
            "#@ panel: main\nHello world.",
            layout,
            Map.of("source_format", "pdf"),
            List.of());

        String result = builder.build(parseResult, "/docs/test.pdf");

        assertThat(result).startsWith("---\n");
        assertThat(result).contains("content_format: positional-markdown");
        assertThat(result).contains("source_format: pdf");
        assertThat(result).contains("source_pages: 1");
        assertThat(result).contains("page_size: [612, 792]");
        assertThat(result).contains("layout: single");
        assertThat(result).contains("artifact_ref: /docs/test.pdf");
        assertThat(result).contains("#@ panel: main\nHello world.");
        assertThat(result).doesNotContain("panels:");
    }

    @Test
    void buildsTwoColumnDocument() {
        var layout = new Layout("2-column",
            List.of(
                new PanelDefinition("left", 0.0, 0.0, 0.48, 1.0),
                new PanelDefinition("right", 0.52, 0.0, 0.48, 1.0)),
            1, new int[]{612, 792});
        var parseResult = new ParseResult(
            "#@ panel: left\nLeft text.\n#@ panel: right\nRight text.",
            layout,
            Map.of("source_format", "pdf"),
            List.of());

        String result = builder.build(parseResult, "/docs/paper.pdf");

        assertThat(result).contains("layout: 2-column");
        assertThat(result).contains("panels:");
        assertThat(result).contains("  - id: left");
        assertThat(result).contains("    x: 0.0");
        assertThat(result).contains("  - id: right");
        assertThat(result).contains("    x: 0.52");
    }

    @Test
    void includesTitle() {
        var layout = new Layout("single",
            List.of(new PanelDefinition("main", 0.0, 0.0, 1.0, 1.0)),
            1, new int[]{612, 792});
        var parseResult = new ParseResult(
            "Content.",
            layout,
            Map.of("source_format", "pdf", "title", "My Document"),
            List.of());

        String result = builder.build(parseResult, null);

        assertThat(result).contains("title: \"My Document\"");
        assertThat(result).doesNotContain("artifact_ref");
    }

    @Test
    void multiPageDocument() {
        var layout = new Layout("single",
            List.of(new PanelDefinition("main", 0.0, 0.0, 1.0, 1.0)),
            5, new int[]{612, 792});
        var parseResult = new ParseResult(
            "#@ page: 1\nPage one.\n#@ page: 2\nPage two.",
            layout,
            Map.of("source_format", "pdf"),
            List.of());

        String result = builder.build(parseResult, "/docs/long.pdf");

        assertThat(result).contains("source_pages: 5");
    }

    @Test
    void generatesUniqueId() {
        var layout = new Layout("single",
            List.of(new PanelDefinition("main", 0.0, 0.0, 1.0, 1.0)),
            1, new int[]{612, 792});
        var parseResult = new ParseResult("Content.", layout, Map.of("source_format", "pdf"), List.of());

        String result1 = builder.build(parseResult, null);
        String result2 = builder.build(parseResult, null);

        String id1 = extractField(result1, "id");
        String id2 = extractField(result2, "id");
        assertThat(id1).startsWith("CE-");
        assertThat(id2).startsWith("CE-");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void rejectsNullParseResult() {
        assertThatThrownBy(() -> builder.build(null, "/ref"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escapesYamlInTitle() {
        var layout = new Layout("single",
            List.of(new PanelDefinition("main", 0.0, 0.0, 1.0, 1.0)),
            1, new int[]{612, 792});
        var parseResult = new ParseResult(
            "Content.",
            layout,
            Map.of("source_format", "pdf", "title", "A \"quoted\" title"),
            List.of());

        String result = builder.build(parseResult, null);

        assertThat(result).contains("title: \"A \\\"quoted\\\" title\"");
    }

    private static String extractField(String yaml, String field) {
        for (String line : yaml.split("\n")) {
            if (line.startsWith(field + ": ")) {
                return line.substring(field.length() + 2).strip();
            }
        }
        return null;
    }
}
