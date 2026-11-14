package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.VersionInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class GitCorpusReaderTest {

    @TempDir Path repoDir;
    private Repository repository;

    @BeforeEach
    void setUp() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            git.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
            git.getRepository().getConfig().save();
            Files.writeString(repoDir.resolve("test.md"), "hello world");
            git.add().addFilepattern("test.md").call();
            git.commit().setMessage("initial").call();
            repository = git.getRepository();
        }
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void readReturnsCommittedContent() {
        var reader = new GitCorpusReader(repository, "HEAD");
        var result = reader.read("test.md");
        assertThat(result).isPresent();
        assertThat(new String(result.get(), UTF_8)).isEqualTo("hello world");
    }

    @Test
    void readNonExistentReturnsEmpty() {
        var reader = new GitCorpusReader(repository, "HEAD");
        assertThat(reader.read("nonexistent.md")).isEmpty();
    }

    @Test
    void listReturnsAllFiles() {
        var reader = new GitCorpusReader(repository, "HEAD");
        assertThat(reader.list()).containsExactly("test.md");
    }

    @Test
    void listWithPrefixFilters() throws Exception {
        try (Git git = new Git(repository)) {
            Files.createDirectories(repoDir.resolve("docs"));
            Files.writeString(repoDir.resolve("docs/a.md"), "a");
            Files.writeString(repoDir.resolve("docs/b.md"), "b");
            git.add().addFilepattern("docs/").call();
            git.commit().setMessage("add docs").call();
        }

        var reader = new GitCorpusReader(repository, "HEAD");
        assertThat(reader.list("docs/")).containsExactlyInAnyOrder("docs/a.md", "docs/b.md");
    }

    @Test
    void existsReturnsTrueForCommittedFile() {
        var reader = new GitCorpusReader(repository, "HEAD");
        assertThat(reader.exists("test.md")).isTrue();
        assertThat(reader.exists("nonexistent.md")).isFalse();
    }

    @Test
    void versionsReturnsCommitHistory() throws Exception {
        try (Git git = new Git(repository)) {
            Files.writeString(repoDir.resolve("test.md"), "updated");
            git.add().addFilepattern("test.md").call();
            git.commit().setMessage("update").call();
        }

        var reader = new GitCorpusReader(repository, "HEAD");
        List<VersionInfo> versions = reader.versions("test.md");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).version()).isEqualTo(1);
        assertThat(versions.get(1).version()).isEqualTo(2);
        assertThat(versions.get(0).source()).hasSize(40);
    }

    @Test
    void readVersionReturnsHistoricalContent() throws Exception {
        try (Git git = new Git(repository)) {
            Files.writeString(repoDir.resolve("test.md"), "v2 content");
            git.add().addFilepattern("test.md").call();
            git.commit().setMessage("v2").call();
        }

        var reader = new GitCorpusReader(repository, "HEAD");
        var v1 = reader.readVersion("test.md", 1);
        var v2 = reader.readVersion("test.md", 2);
        assertThat(v1).isPresent();
        assertThat(v2).isPresent();
        assertThat(new String(v1.get(), UTF_8)).isEqualTo("hello world");
        assertThat(new String(v2.get(), UTF_8)).isEqualTo("v2 content");
    }

    @Test
    void readUncommittedContentReturnsEmpty() throws Exception {
        Files.writeString(repoDir.resolve("uncommitted.md"), "draft");
        var reader = new GitCorpusReader(repository, "HEAD");
        assertThat(reader.exists("uncommitted.md")).isFalse();
    }

    @Test
    void readStreamReturnsContent() throws Exception {
        var reader = new GitCorpusReader(repository, "HEAD");
        var stream = reader.readStream("test.md");
        assertThat(stream).isPresent();
        assertThat(new String(stream.get().readAllBytes(), UTF_8)).isEqualTo("hello world");
    }

    @Test
    void versionsEmptyForNonExistent() {
        var reader = new GitCorpusReader(repository, "HEAD");
        assertThat(reader.versions("nonexistent.md")).isEmpty();
    }
}
