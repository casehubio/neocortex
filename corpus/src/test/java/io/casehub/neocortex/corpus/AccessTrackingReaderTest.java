package io.casehub.neocortex.corpus;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTrackingReaderTest {

    @Test
    void readEmitsAccessEvent() {
        var events = new ArrayList<AccessEvent>();
        CorpusReader delegate = stubReader("doc.md", "content".getBytes());
        var tracked = new AccessTrackingReader(delegate, "test-corpus", events::add);

        tracked.read("doc.md");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).path()).isEqualTo("doc.md");
        assertThat(events.get(0).corpusName()).isEqualTo("test-corpus");
        assertThat(events.get(0).timestamp()).isNotNull();
    }

    @Test
    void readMissDoesNotEmitEvent() {
        var events = new ArrayList<AccessEvent>();
        CorpusReader delegate = emptyReader();
        var tracked = new AccessTrackingReader(delegate, "test-corpus", events::add);

        tracked.read("missing.md");

        assertThat(events).isEmpty();
    }

    @Test
    void delegatesListToWrapped() {
        CorpusReader delegate = stubReader("a.md", "content".getBytes());
        var tracked = new AccessTrackingReader(delegate, "test-corpus", e -> {});

        assertThat(tracked.list()).containsExactly("a.md");
    }

    @Test
    void delegatesExistsToWrapped() {
        CorpusReader delegate = stubReader("a.md", "content".getBytes());
        var tracked = new AccessTrackingReader(delegate, "test-corpus", e -> {});

        assertThat(tracked.exists("a.md")).isTrue();
        assertThat(tracked.exists("missing.md")).isFalse();
    }

    private static CorpusReader stubReader(String path, byte[] content) {
        return new CorpusReader() {
            @Override public Optional<byte[]> read(String p) { return p.equals(path) ? Optional.of(content) : Optional.empty(); }
            @Override public Optional<InputStream> readStream(String p) { return Optional.empty(); }
            @Override public Optional<byte[]> readVersion(String p, int v) { return Optional.empty(); }
            @Override public List<VersionInfo> versions(String p) { return List.of(); }
            @Override public List<String> list() { return List.of(path); }
            @Override public List<String> list(String prefix) { return List.of(); }
            @Override public boolean exists(String p) { return p.equals(path); }
        };
    }

    private static CorpusReader emptyReader() {
        return new CorpusReader() {
            @Override public Optional<byte[]> read(String p) { return Optional.empty(); }
            @Override public Optional<InputStream> readStream(String p) { return Optional.empty(); }
            @Override public Optional<byte[]> readVersion(String p, int v) { return Optional.empty(); }
            @Override public List<VersionInfo> versions(String p) { return List.of(); }
            @Override public List<String> list() { return List.of(); }
            @Override public List<String> list(String prefix) { return List.of(); }
            @Override public boolean exists(String p) { return false; }
        };
    }
}
