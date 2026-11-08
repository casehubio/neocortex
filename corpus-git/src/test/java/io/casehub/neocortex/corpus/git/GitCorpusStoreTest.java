package io.casehub.neocortex.corpus.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class GitCorpusStoreTest {

    @TempDir Path repoDir;
    private Repository repository;

    @BeforeEach
    void setUp() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve(".gitkeep"), "");
            git.add().addFilepattern(".gitkeep").call();
            git.commit().setMessage("init").call();
            repository = git.getRepository();
        }
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void writeAndFlushMakesContentVisibleToReader() {
        var store = new GitCorpusStore(repository);
        var reader = new GitCorpusReader(repository, "HEAD");

        store.write("doc.md", "hello git".getBytes(UTF_8));
        assertThat(reader.exists("doc.md")).isFalse();

        store.flush();
        assertThat(reader.exists("doc.md")).isTrue();
        assertThat(new String(reader.read("doc.md").orElseThrow(), UTF_8)).isEqualTo("hello git");
    }

    @Test
    void batchWriteFlushesOneCommit() throws Exception {
        var store = new GitCorpusStore(repository);
        store.write("a.md", "aaa".getBytes(UTF_8));
        store.write("b.md", "bbb".getBytes(UTF_8));
        store.flush();

        try (Git git = new Git(repository)) {
            int commitCount = 0;
            for (RevCommit ignored : git.log().call()) commitCount++;
            assertThat(commitCount).isEqualTo(2); // init + one batch commit
        }
    }

    @Test
    void deleteAndFlushRemovesFromReader() {
        var store = new GitCorpusStore(repository);
        var reader = new GitCorpusReader(repository, "HEAD");

        store.write("to-delete.md", "temp".getBytes(UTF_8));
        store.flush();
        assertThat(reader.exists("to-delete.md")).isTrue();

        store.delete("to-delete.md");
        store.flush();
        assertThat(reader.exists("to-delete.md")).isFalse();
    }

    @Test
    void writeCreatesSubdirectories() {
        var store = new GitCorpusStore(repository);
        var reader = new GitCorpusReader(repository, "HEAD");

        store.write("docs/sub/deep.md", "nested".getBytes(UTF_8));
        store.flush();

        assertThat(reader.exists("docs/sub/deep.md")).isTrue();
        assertThat(reader.list("docs/")).containsExactly("docs/sub/deep.md");
    }

    @Test
    void flushWithNoPendingWritesIsNoOp() {
        var store = new GitCorpusStore(repository);
        store.flush(); // should not throw
    }

    @Test
    void writeOverwritesPreviousContent() {
        var store = new GitCorpusStore(repository);
        var reader = new GitCorpusReader(repository, "HEAD");

        store.write("doc.md", "v1".getBytes(UTF_8));
        store.flush();
        store.write("doc.md", "v2".getBytes(UTF_8));
        store.flush();

        assertThat(new String(reader.read("doc.md").orElseThrow(), UTF_8)).isEqualTo("v2");
        assertThat(reader.versions("doc.md")).hasSize(2);
    }
}
