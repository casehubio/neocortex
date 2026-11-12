package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.HtmlToMarkdownParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class HtmlToMarkdownParserAdapter implements HtmlToMarkdownParser {

    private final HtmlDocumentParser parser = new HtmlDocumentParser();
    private final PositionalMarkdownBuilder builder = new PositionalMarkdownBuilder();

    @Override
    public String parse(String html, String sourceUrl) {
        var result = parser.parse(
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
                sourceUrl,
                DocumentParserConfig.defaults());
        return builder.build(result, null);
    }
}
