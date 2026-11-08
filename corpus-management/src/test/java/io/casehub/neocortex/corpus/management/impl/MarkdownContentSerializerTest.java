package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class MarkdownContentSerializerTest {

    @Test
    void serializesEntryWithMetadataAndBody() {
        var entry = new CorpusEntry("test-id",
                Map.of("title", "Test", "domain", "tools"), "The body text.", null);
        var serializer = new MarkdownContentSerializer();

        byte[] result = serializer.serialize(entry);
        String content = new String(result, UTF_8);

        assertThat(content).contains("---");
        assertThat(content).contains("title: Test");
        assertThat(content).contains("domain: tools");
        assertThat(content).contains("The body text.");
    }

    @Test
    void serializesListMetadata() {
        var entry = new CorpusEntry("test-id",
                Map.of("tags", List.of("java", "quarkus")), "Body.", null);
        var serializer = new MarkdownContentSerializer();

        String content = new String(serializer.serialize(entry), UTF_8);

        assertThat(content).contains("tags:");
        assertThat(content).contains("  - java");
        assertThat(content).contains("  - quarkus");
    }

    @Test
    void serializesWithNullBody() {
        var entry = new CorpusEntry("test-id", Map.of("title", "Empty"), null, null);
        var serializer = new MarkdownContentSerializer();

        String content = new String(serializer.serialize(entry), UTF_8);

        assertThat(content).contains("title: Empty");
        assertThat(content).doesNotContain("null");
    }

    @Test
    void metadataIsSortedAlphabetically() {
        var entry = new CorpusEntry("test-id",
                Map.of("zebra", "last", "alpha", "first"), "Body.", null);
        var serializer = new MarkdownContentSerializer();

        String content = new String(serializer.serialize(entry), UTF_8);

        int alphaPos = content.indexOf("alpha:");
        int zebraPos = content.indexOf("zebra:");
        assertThat(alphaPos).isLessThan(zebraPos);
    }
}
