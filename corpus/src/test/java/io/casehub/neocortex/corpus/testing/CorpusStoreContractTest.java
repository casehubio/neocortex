package io.casehub.neocortex.corpus.testing;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.CorpusStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class CorpusStoreContractTest {

    protected abstract CorpusStore store();

    protected abstract CorpusReader reader();

    @Test
    void appendBytesThenReadReturnsSameContent() {
        byte[] content = "hello world".getBytes();
        store().append("doc.txt", content);

        var read = reader().read("doc.txt");
        assertThat(read).isPresent();
        assertThat(read.get()).isEqualTo(content);
    }

    @Test
    void appendStreamThenReadReturnsSameContent() {
        byte[] content = "stream content".getBytes();
        store().append("stream.txt", new ByteArrayInputStream(content));

        var read = reader().read("stream.txt");
        assertThat(read).isPresent();
        assertThat(read.get()).isEqualTo(content);
    }

    @Test
    void appendThenListIncludesPath() {
        store().append("listed.txt", "content".getBytes());

        assertThat(reader().list()).contains("listed.txt");
    }

    @Test
    void appendThenExistsReturnsTrue() {
        store().append("exists.txt", "content".getBytes());

        assertThat(reader().exists("exists.txt")).isTrue();
    }

    @Test
    void readNonExistentReturnsEmpty() {
        assertThat(reader().read("nonexistent.txt")).isEmpty();
    }

    @Test
    void readStreamNonExistentReturnsEmpty() {
        assertThat(reader().readStream("nonexistent.txt")).isEmpty();
    }

    @Test
    void existsNonExistentReturnsFalse() {
        assertThat(reader().exists("nonexistent.txt")).isFalse();
    }

    @Test
    void deleteRemovesEntry() {
        store().append("to-delete.txt", "content".getBytes());
        assertThat(reader().exists("to-delete.txt")).isTrue();

        store().delete("to-delete.txt");

        assertThat(reader().exists("to-delete.txt")).isFalse();
        assertThat(reader().read("to-delete.txt")).isEmpty();
    }

    @Test
    void listWithPrefixFiltersCorrectly() {
        store().append("docs/a.md", "a".getBytes());
        store().append("docs/b.md", "b".getBytes());
        store().append("other/c.md", "c".getBytes());

        var filtered = reader().list("docs/");
        assertThat(filtered).containsExactlyInAnyOrder("docs/a.md", "docs/b.md");
    }

    @Test
    void readStreamReturnsContent() throws IOException {
        byte[] content = "stream test".getBytes();
        store().append("readable.txt", content);

        var stream = reader().readStream("readable.txt");
        assertThat(stream).isPresent();
        assertThat(stream.get().readAllBytes()).isEqualTo(content);
    }

    @Test
    void versionsReturnsNonEmptyAfterAppend() {
        store().append("versioned.txt", "content".getBytes());

        var versions = reader().versions("versioned.txt");
        assertThat(versions).isNotEmpty();
        assertThat(versions.get(0).version()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void versionsReturnsEmptyForNonExistent() {
        assertThat(reader().versions("nonexistent.txt")).isEmpty();
    }

    @Test
    void readVersionOneMatchesCurrentContent() {
        byte[] content = "version content".getBytes();
        store().append("v1.txt", content);

        var read = reader().readVersion("v1.txt", 1);
        assertThat(read).isPresent();
        assertThat(read.get()).isEqualTo(content);
    }

    @Test
    void listReturnsEmptyInitially() {
        assertThat(reader().list()).isEmpty();
    }
}
