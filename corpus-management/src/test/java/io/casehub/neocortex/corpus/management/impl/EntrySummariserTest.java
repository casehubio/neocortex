package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.rag.ExtractionResult;
import io.casehub.neocortex.rag.MetadataExtractor;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class EntrySummariserTest {

    @Test
    void summariseExtractsTitleAndVersionInfo() {
        Instant modified = Instant.parse("2026-11-07T10:00:00Z");
        CorpusReader reader = stubReader("doc.md", "---\ntitle: Test\n---\nBody".getBytes(UTF_8),
                List.of(new VersionInfo(1, modified, "abc123")));
        MetadataExtractor extractor = (path, content) ->
                new ExtractionResult("Body", Map.of("title", "Test"));

        var summariser = new EntrySummariser(reader, extractor);
        EntrySummary summary = summariser.summarise("doc.md");

        assertThat(summary.id()).isEqualTo("doc.md");
        assertThat(summary.title()).isEqualTo("Test");
        assertThat(summary.lastModified()).isEqualTo(modified);
        assertThat(summary.versionCount()).isEqualTo(1);
    }

    @Test
    void summariseHandlesMissingContent() {
        CorpusReader reader = stubReader("missing.md", null,
                List.of(new VersionInfo(1, Instant.now(), "abc")));

        var summariser = new EntrySummariser(reader, (p, c) -> new ExtractionResult("", Map.of()));
        EntrySummary summary = summariser.summarise("missing.md");

        assertThat(summary.id()).isEqualTo("missing.md");
        assertThat(summary.title()).isNull();
        assertThat(summary.versionCount()).isEqualTo(1);
    }

    @Test
    void summariseHandlesNoVersions() {
        CorpusReader reader = stubReader("new.md", "content".getBytes(UTF_8), List.of());
        MetadataExtractor extractor = (path, content) ->
                new ExtractionResult("content", Map.of("title", "New"));

        var summariser = new EntrySummariser(reader, extractor);
        EntrySummary summary = summariser.summarise("new.md");

        assertThat(summary.lastModified()).isNull();
        assertThat(summary.versionCount()).isEqualTo(0);
    }

    @Test
    void summariseWithMultipleVersions() {
        Instant v1 = Instant.parse("2026-11-01T00:00:00Z");
        Instant v2 = Instant.parse("2026-11-07T00:00:00Z");
        CorpusReader reader = stubReader("doc.md", "content".getBytes(UTF_8),
                List.of(new VersionInfo(1, v1, "aaa"), new VersionInfo(2, v2, "bbb")));
        MetadataExtractor extractor = (path, content) ->
                new ExtractionResult("content", Map.of("title", "Doc"));

        var summariser = new EntrySummariser(reader, extractor);
        EntrySummary summary = summariser.summarise("doc.md");

        assertThat(summary.versionCount()).isEqualTo(2);
        assertThat(summary.lastModified()).isEqualTo(v2);
    }

    private static CorpusReader stubReader(String path, byte[] content, List<VersionInfo> versions) {
        return new CorpusReader() {
            @Override public Optional<byte[]> read(String p) {
                return p.equals(path) && content != null ? Optional.of(content) : Optional.empty();
            }
            @Override public Optional<InputStream> readStream(String p) { return Optional.empty(); }
            @Override public Optional<byte[]> readVersion(String p, int v) { return Optional.empty(); }
            @Override public List<VersionInfo> versions(String p) { return p.equals(path) ? versions : List.of(); }
            @Override public List<String> list() { return List.of(path); }
            @Override public List<String> list(String prefix) { return List.of(); }
            @Override public boolean exists(String p) { return p.equals(path); }
        };
    }
}
