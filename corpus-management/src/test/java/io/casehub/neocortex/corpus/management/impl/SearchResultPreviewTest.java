package io.casehub.neocortex.corpus.management.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResultPreviewTest {

    private static SearchResult result(String body) {
        return new SearchResult("id-1", "Title", "jvm", "guide", 80, body,
                0.95, null, "corpus-1", "c1", List.of(), Map.of());
    }

    @Test
    void withPreviewTruncatesLongBody() {
        var r = result("A".repeat(1000));
        var preview = r.withPreview(200);

        assertThat(preview.body()).hasSize(203);
        assertThat(preview.body()).endsWith("...");
        assertThat(preview.id()).isEqualTo("id-1");
        assertThat(preview.relevance()).isEqualTo(0.95);
    }

    @Test
    void withPreviewKeepsShortBodyUnchanged() {
        var r = result("Short body.");
        var preview = r.withPreview(200);

        assertThat(preview.body()).isEqualTo("Short body.");
    }

    @Test
    void withPreviewKeepsExactLengthUnchanged() {
        var body = "X".repeat(200);
        var r = result(body);
        var preview = r.withPreview(200);

        assertThat(preview.body()).isEqualTo(body);
    }

    @Test
    void withPreviewHandlesNullBody() {
        var r = new SearchResult("id-1", "Title", "jvm", "guide", 80, null,
                0.95, null, "corpus-1", "c1", List.of(), Map.of());
        var preview = r.withPreview(200);

        assertThat(preview.body()).isNull();
    }

    @Test
    void adaptiveResultWithResultsPreservesMetadata() {
        var results = List.of(result("Body 1"), result("Body 2"));
        var adaptive = new AdaptiveResult(results, 10, 5, false, true, 2);

        var previewed = List.of(result("B1"), result("B2"));
        var updated = adaptive.withResults(previewed);

        assertThat(updated.results()).hasSize(2);
        assertThat(updated.requestedLimit()).isEqualTo(10);
        assertThat(updated.trimmed()).isTrue();
        assertThat(updated.floorFiltered()).isEqualTo(2);
    }
}
