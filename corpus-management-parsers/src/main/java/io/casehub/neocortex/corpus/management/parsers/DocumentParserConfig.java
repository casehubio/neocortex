package io.casehub.neocortex.corpus.management.parsers;

public record DocumentParserConfig(
    boolean ocrEnabled,
    boolean llmImageDescriptionEnabled,
    boolean llmLayoutDetectionEnabled,
    int maxPages
) {
    public static DocumentParserConfig defaults() {
        return new DocumentParserConfig(true, false, false, Integer.MAX_VALUE);
    }
}
