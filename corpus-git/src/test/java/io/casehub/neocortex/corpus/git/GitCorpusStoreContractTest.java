package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.CorpusStore;
import io.casehub.neocortex.corpus.testing.CorpusStoreContractTest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class GitCorpusStoreContractTest extends CorpusStoreContractTest {

    @TempDir Path repoDir;
    private Repository repository;
    private GitCorpusStore store;
    private GitCorpusReader reader;

    @BeforeEach
    void setUp() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve(".gitkeep"), "");
            git.add().addFilepattern(".gitkeep").call();
            git.commit().setMessage("init").call();
            repository = git.getRepository();
        }
        store = new GitCorpusStore(repository);
        reader = new GitCorpusReader(repository, "HEAD");
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Override
    protected CorpusStore store() {
        return store;
    }

    @Override
    protected CorpusReader reader() {
        return reader;
    }
}
