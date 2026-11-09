package io.casehub.neocortex.corpus.management;

@FunctionalInterface
public interface HtmlToMarkdownParser {
    String parse(String html, String sourceUrl);
}
