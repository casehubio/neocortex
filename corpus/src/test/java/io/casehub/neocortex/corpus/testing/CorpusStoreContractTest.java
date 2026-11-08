package io.casehub.neocortex.corpus.testing;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.CorpusStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class CorpusStoreContractTest {

    protected abstract CorpusStore store();

    protected abstract CorpusReader reader();

    @Test
    void writeBytesThenReadReturnsSameContent() {
        byte[] content = "hello world".getBytes();
        store().write("doc.txt", content);
        store().flush();

        var read = reader().read("doc.txt");
        assertThat(read).isPresent();
        assertThat(read.get()).isEqualTo(content);
    }

    @Test
    void writeStreamThenReadReturnsSameContent() {
        byte[] content = "stream content".getBytes();
        store().write("stream.txt", new ByteArrayInputStream(content));
        store().flush();

        var read = reader().read("stream.txt");
        assertThat(read).isPresent();
        assertThat(read.get()).isEqualTo(content);
    }

    @Test
    void writeThenListIncludesPath() {
        store().write("listed.txt", "content".getBytes());
        store().flush();

        assertThat(reader().list()).contains("listed.txt");
    }

    @Test
    void writeThenExistsReturnsTrue() {
        store().write("exists.txt", "content".getBytes());
        store().flush();

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
        store().write("to-delete.txt", "content".getBytes());
        store().flush();
        assertThat(reader().exists("to-delete.txt")).isTrue();

        store().delete("to-delete.txt");
        store().flush();

        assertThat(reader().exists("to-delete.txt")).isFalse();
        assertThat(reader().read("to-delete.txt")).isEmpty();
    }

    @Test
    void listWithPrefixFiltersCorrectly() {
        store().write("docs/a.md", "a".getBytes());
        store().write("docs/b.md", "b".getBytes());
        store().write("other/c.md", "c".getBytes());
        store().flush();

        var filtered = reader().list("docs/");
        assertThat(filtered).containsExactlyInAnyOrder("docs/a.md", "docs/b.md");
    }

    @Test
    void readStreamReturnsContent() throws IOException {
        byte[] content = "stream test".getBytes();
        store().write("readable.txt", content);
        store().flush();

        var stream = reader().readStream("readable.txt");
        assertThat(stream).isPresent();
        assertThat(stream.get().readAllBytes()).isEqualTo(content);
    }

    @Test
    void versionsReturnsNonEmptyAfterWrite() {
        store().write("versioned.txt", "content".getBytes());
        store().flush();

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
        store().write("v1.txt", content);
        store().flush();

        var read = reader().readVersion("v1.txt", 1);
        assertThat(read).isPresent();
        assertThat(read.get()).isEqualTo(content);
    }

    @Test
    void listReturnsEmptyInitially() {
        assertThat(reader().list()).isEmpty();
    }

    @Test
    void flushDoesNotThrow() {
        store().write("flush-test.txt", "content".getBytes());
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> store().flush());
    }

    @Test
    void writeAndFlushThenRead() {
        store().write("flush-read.txt", "hello".getBytes());
        store().flush();
        var result = reader().read("flush-read.txt");
        assertThat(result).isPresent();
        assertThat(new String(result.get())).isEqualTo("hello");
    }
}
