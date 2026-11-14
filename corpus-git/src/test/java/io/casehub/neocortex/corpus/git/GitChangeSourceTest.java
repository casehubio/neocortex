package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.ChangeSet;
import io.casehub.neocortex.corpus.ChangeType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitChangeSourceTest {

    @TempDir Path repoDir;
    private Repository repository;

    @BeforeEach
    void setUp() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            git.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
            git.getRepository().getConfig().save();
            Files.writeString(repoDir.resolve("initial.md"), "hello");
            git.add().addFilepattern("initial.md").call();
            git.commit().setMessage("initial").call();
            repository = git.getRepository();
        }
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void changesSinceDetectsNewFile() throws Exception {
        String cursor;
        try (var cs = new GitChangeSource(repository, "HEAD")) {
            cursor = cs.currentCursor();
        }

        try (Git git = new Git(repository)) {
            Files.writeString(repoDir.resolve("new.md"), "new content");
            git.add().addFilepattern("new.md").call();
            git.commit().setMessage("add new").call();
        }

        try (var cs = new GitChangeSource(repository, "HEAD")) {
            ChangeSet changes = cs.changesSince(cursor);
            assertThat(changes.entries()).hasSize(1);
            assertThat(changes.entries().get(0).path()).isEqualTo("new.md");
            assertThat(changes.entries().get(0).type()).isEqualTo(ChangeType.ADDED);
        }
    }

    @Test
    void changesSinceDetectsModification() throws Exception {
        String cursor;
        try (var cs = new GitChangeSource(repository, "HEAD")) {
            cursor = cs.currentCursor();
        }

        try (Git git = new Git(repository)) {
            Files.writeString(repoDir.resolve("initial.md"), "modified");
            git.add().addFilepattern("initial.md").call();
            git.commit().setMessage("modify").call();
        }

        try (var cs = new GitChangeSource(repository, "HEAD")) {
            ChangeSet changes = cs.changesSince(cursor);
            assertThat(changes.entries()).hasSize(1);
            assertThat(changes.entries().get(0).type()).isEqualTo(ChangeType.MODIFIED);
        }
    }

    @Test
    void changesSinceDetectsDeletion() throws Exception {
        String cursor;
        try (var cs = new GitChangeSource(repository, "HEAD")) {
            cursor = cs.currentCursor();
        }

        try (Git git = new Git(repository)) {
            git.rm().addFilepattern("initial.md").call();
            git.commit().setMessage("delete").call();
        }

        try (var cs = new GitChangeSource(repository, "HEAD")) {
            ChangeSet changes = cs.changesSince(cursor);
            assertThat(changes.entries()).hasSize(1);
            assertThat(changes.entries().get(0).type()).isEqualTo(ChangeType.DELETED);
        }
    }

    @Test
    void fullScanListsAllFiles() {
        try (var cs = new GitChangeSource(repository, "HEAD")) {
            ChangeSet scan = cs.fullScan();
            assertThat(scan.entries()).hasSize(1);
            assertThat(scan.entries().get(0).path()).isEqualTo("initial.md");
            assertThat(scan.entries().get(0).type()).isEqualTo(ChangeType.ADDED);
            assertThat(scan.newCursor()).isNotNull().hasSize(40);
        }
    }

    @Test
    void currentCursorReturnsSha() {
        try (var cs = new GitChangeSource(repository, "HEAD")) {
            String cursor = cs.currentCursor();
            assertThat(cursor).isNotNull().hasSize(40);
        }
    }

    @Test
    void changesSinceWithSameCursorReturnsEmpty() {
        try (var cs = new GitChangeSource(repository, "HEAD")) {
            String cursor = cs.currentCursor();
            ChangeSet changes = cs.changesSince(cursor);
            assertThat(changes.entries()).isEmpty();
        }
    }

    @Test
    void changesSinceWithNullCursorReturnsFullScan() {
        try (var cs = new GitChangeSource(repository, "HEAD")) {
            ChangeSet changes = cs.changesSince(null);
            assertThat(changes.entries()).hasSize(1);
            assertThat(changes.entries().get(0).path()).isEqualTo("initial.md");
        }
    }
}
